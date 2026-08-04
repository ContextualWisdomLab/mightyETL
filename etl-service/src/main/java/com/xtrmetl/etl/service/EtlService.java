package com.xtrmetl.etl.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates, transforms, and loads one bounded ETL request batch.
 *
 * <p>The complete payload is parsed and transformed before the first JDBC call. Accepted records
 * are then written synchronously inside one Spring transaction, so a runtime failure rolls the
 * batch back rather than leaving committed prefix records. The service intentionally avoids the
 * JVM common pool and one-task-per-record fan-out.</p>
 *
 * <p>Callers may optionally use {@link #processDataIdempotently(String, String, String)}. That
 * method serializes requests by a principal-scoped key hash, replays a prior successful response,
 * and commits the ETL rows and durable ledger entry in the same transaction.</p>
 *
 * <p>Deterministic request rejections use {@link EtlRequestException} so the HTTP boundary can
 * expose stable RFC 9457 classifications without copying parser, validation, SQL, or internal
 * exception text into a response.</p>
 */
@Service
public class EtlService {

    private static final String INSERT_SQL = "INSERT INTO processed_data (data) VALUES (?)";
    private static final String SELECT_IDEMPOTENCY_SQL = """
            SELECT request_digest, response_body
            FROM etl_idempotency_records
            WHERE idempotency_key_hash = ?
            """;
    private static final String INSERT_IDEMPOTENCY_SQL = """
            INSERT INTO etl_idempotency_records (
                idempotency_key_hash,
                request_digest,
                response_body
            ) VALUES (?, ?, ?)
            """;
    private static final int MAX_AMOUNT_PRECISION = 38;
    private static final int MAX_AMOUNT_ABSOLUTE_SCALE = 18;
    private static final int MAX_RECORD_ID_CODE_POINTS = 256;
    private static final int MAX_PRINCIPAL_SCOPE_CODE_POINTS = 512;
    private static final Pattern OUTER_IDENTIFIER_WHITESPACE = Pattern.compile(
            "^\\s|\\s$",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final String IDEMPOTENCY_KEY_VALUE_EXPRESSION = "[A-Za-z0-9._:-]{16,128}";
    private static final Pattern IDEMPOTENCY_KEY_VALUE_PROFILE = Pattern.compile(
            IDEMPOTENCY_KEY_VALUE_EXPRESSION
    );
    private static final Pattern IDEMPOTENCY_KEY_STRUCTURED_FIELD_PROFILE = Pattern.compile(
            "\"(" + IDEMPOTENCY_KEY_VALUE_EXPRESSION + ")\""
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EtlBatchProperties batchProperties;
    private final EtlRequestLock requestLock;

    /**
     * Creates the ETL service with safe defaults and the PostgreSQL request-lock implementation.
     *
     * <p>This constructor remains available for direct construction by existing integrations and
     * tests. Spring production wiring uses the four-argument constructor so the lock adapter can
     * be replaced explicitly where needed.</p>
     *
     * @param jdbcTemplate parameterized database access
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties request safety limits; {@code null} uses safe defaults
     */
    public EtlService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Nullable EtlBatchProperties batchProperties
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                batchProperties,
                new PostgresEtlRequestLock(jdbcTemplate)
        );
    }

    /**
     * Creates the ETL service with an isolated parser and transaction-lifetime request lock.
     *
     * <p>The injected mapper is copied before strict duplicate detection is enabled so this
     * service cannot mutate shared application-wide JSON parsing behavior.</p>
     *
     * @param jdbcTemplate parameterized database access
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties request safety limits; {@code null} uses safe defaults for legacy
     *                        direct construction and Mockito-based tests
     * @param requestLock transaction-lifetime idempotency request lock
     */
    @Autowired
    public EtlService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Nullable EtlBatchProperties batchProperties,
            EtlRequestLock requestLock
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        ObjectMapper sourceMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
        this.objectMapper = sourceMapper.copy();
        this.objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.batchProperties = batchProperties == null
                ? new EtlBatchProperties()
                : batchProperties;
        this.requestLock = Objects.requireNonNull(requestLock, "requestLock must not be null");
    }

    /**
     * Processes one JSON-array request as a prevalidated transaction-scoped batch.
     *
     * <p>Only transient Spring data-access failures are retried. Typed input failures and
     * deterministic target constraints fail immediately instead of repeating the same work.</p>
     *
     * @param data UTF-8 JSON array payload
     * @return one {@code Processed: <id>} line per record, in input order
     * @throws EtlRequestException when the request violates an admission or semantic contract
     * @throws org.springframework.dao.DataAccessException when the target database rejects work
     */
    @Retryable(
            retryFor = TransientDataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public String processData(@Nullable String data) {
        return processDataInCurrentTransaction(data);
    }

    /**
     * Processes or replays one principal-scoped idempotent ETL request.
     *
     * <p>The client key and authenticated principal are never stored in plaintext. A standards-
     * shaped RFC 8941 quoted String and the retained legacy raw representation are normalized to
     * the same semantic key before its principal-scoped SHA-256 hash is calculated. A transaction-
     * level lock serializes competing requests. Reusing a committed key with the same payload
     * returns the original response without another target write. Reusing it with a different
     * payload is rejected.</p>
     *
     * <p>The target writes and ledger insert share one transaction. A target or ledger failure
     * therefore leaves neither a partial batch nor a false successful replay record. Transient
     * database failures retry the complete transaction rather than only one statement. The method
     * fails closed before lock or JDBC access when called without an active Spring transaction,
     * which prevents direct construction or self-invocation from silently weakening durability.</p>
     *
     * @param data UTF-8 JSON array payload
     * @param idempotencyKey quoted RFC 8941 String or legacy raw safe-ASCII key of 16 to 128 characters
     * @param principalScope authenticated principal name used only to isolate the key namespace
     * @return response body and whether it was replayed from the durable ledger
     * @throws EtlRequestException when the key, principal, payload, or record contract is invalid
     * @throws IllegalStateException when no actual transaction is active for the idempotent work
     * @throws org.springframework.dao.DataAccessException when the target database rejects work
     */
    @Retryable(
            retryFor = TransientDataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public EtlIdempotencyResult processDataIdempotently(
            @Nullable String data,
            @Nullable String idempotencyKey,
            @Nullable String principalScope
    ) {
        String validatedKey = validateIdempotencyKey(idempotencyKey);
        String validatedScope = validatePrincipalScope(principalScope);
        if (data == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        requireActiveTransaction();
        enforcePayloadLimit(data);

        String idempotencyKeyHash = sha256(
                validatedScope.length() + ":" + validatedScope + ":" + validatedKey
        );
        String requestDigest = sha256(data);

        requestLock.lock(idempotencyKeyHash);
        StoredIdempotencyRecord storedRecord = findStoredIdempotencyRecord(idempotencyKeyHash);
        if (storedRecord != null) {
            if (!storedRecord.requestDigest().equals(requestDigest)) {
                throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_KEY_REUSED);
            }
            return new EtlIdempotencyResult(storedRecord.responseBody(), true);
        }

        String responseBody = processDataInCurrentTransaction(data);
        jdbcTemplate.update(
                INSERT_IDEMPOTENCY_SQL,
                idempotencyKeyHash,
                requestDigest,
                responseBody
        );
        return new EtlIdempotencyResult(responseBody, false);
    }

    private String processDataInCurrentTransaction(@Nullable String data) {
        if (data == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        enforcePayloadLimit(data);

        final JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (JsonProcessingException exception) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON, exception);
        }

        List<ProcessedRecord> preparedRecords = prepareBatch(root);
        for (ProcessedRecord record : preparedRecords) {
            jdbcTemplate.update(INSERT_SQL, record.data());
        }

        return preparedRecords.stream()
                .map(record -> "Processed: " + record.id())
                .collect(Collectors.joining("\n"));
    }

    private StoredIdempotencyRecord findStoredIdempotencyRecord(String idempotencyKeyHash) {
        List<StoredIdempotencyRecord> records = jdbcTemplate.query(
                SELECT_IDEMPOTENCY_SQL,
                (resultSet, rowNumber) -> new StoredIdempotencyRecord(
                        resultSet.getString("request_digest"),
                        resultSet.getString("response_body")
                ),
                idempotencyKeyHash
        );
        return records.isEmpty() ? null : records.getFirst();
    }

    private static String validateIdempotencyKey(@Nullable String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_IDEMPOTENCY_KEY);
        }
        var structuredFieldMatcher = IDEMPOTENCY_KEY_STRUCTURED_FIELD_PROFILE.matcher(idempotencyKey);
        if (structuredFieldMatcher.matches()) {
            return structuredFieldMatcher.group(1);
        }
        if (IDEMPOTENCY_KEY_VALUE_PROFILE.matcher(idempotencyKey).matches()) {
            return idempotencyKey;
        }
        throw new EtlRequestException(EtlRequestError.INVALID_IDEMPOTENCY_KEY);
    }

    private static String validatePrincipalScope(@Nullable String principalScope) {
        if (principalScope == null
                || principalScope.isBlank()
                || principalScope.codePointCount(0, principalScope.length())
                > MAX_PRINCIPAL_SCOPE_CODE_POINTS) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }
        return principalScope;
    }

    private static void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Idempotent ETL processing requires an active transaction"
            );
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private void enforcePayloadLimit(String payload) {
        int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > batchProperties.getMaxPayloadBytes()) {
            throw new EtlRequestException(EtlRequestError.PAYLOAD_TOO_LARGE);
        }
    }

    private List<ProcessedRecord> prepareBatch(@Nullable JsonNode root) {
        if (root == null || root.isNull() || !root.isArray()) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        if (root.size() > batchProperties.getMaxBatchRecords()) {
            throw new EtlRequestException(EtlRequestError.BATCH_TOO_LARGE);
        }

        List<ProcessedRecord> preparedRecords = new ArrayList<>(root.size());
        int index = 0;
        for (JsonNode record : root) {
            preparedRecords.add(prepareRecord(record, index));
            index++;
        }
        return List.copyOf(preparedRecords);
    }

    private ProcessedRecord prepareRecord(@Nullable JsonNode record, int index) {
        if (record == null || !record.isObject()) {
            throw invalidRecord();
        }

        JsonNode idNode = record.get("id");
        if (idNode == null || !idNode.isTextual()) {
            throw invalidRecord();
        }

        String id = idNode.asText();
        int codePointCount = id.codePointCount(0, id.length());
        boolean hasUnsafeCodePoint = id.codePoints().anyMatch(EtlService::isUnsafeIdentifierCodePoint);
        boolean hasOuterUnicodeWhitespace = OUTER_IDENTIFIER_WHITESPACE.matcher(id).find();
        if (id.isBlank()
                || !id.equals(id.strip())
                || hasOuterUnicodeWhitespace
                || codePointCount > MAX_RECORD_ID_CODE_POINTS
                || hasUnsafeCodePoint) {
            throw invalidRecord();
        }

        String transformedData = transformRecord(record, index);
        return new ProcessedRecord(id, transformedData);
    }

    private static boolean isUnsafeIdentifierCodePoint(int codePoint) {
        int characterType = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || characterType == Character.FORMAT
                || characterType == Character.LINE_SEPARATOR
                || characterType == Character.PARAGRAPH_SEPARATOR;
    }

    private static EtlRequestException invalidRecord() {
        return new EtlRequestException(EtlRequestError.INVALID_RECORD);
    }

    private String transformRecord(JsonNode record, int index) {
        StringBuilder transformed = new StringBuilder();
        Set<String> normalizedKeys = new HashSet<>();
        for (Map.Entry<String, JsonNode> field : record.properties()) {
            String key = field.getKey().toUpperCase(Locale.ROOT);
            if (!normalizedKeys.add(key)) {
                throw invalidRecord();
            }
            String value = transformValue(key, field.getValue());
            transformed.append(key).append(":").append(value).append(",");
        }
        return transformed.toString();
    }

    private String transformValue(String key, @Nullable JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return "null";
        }
        if (valueNode.isContainerNode()) {
            return valueNode.toString();
        }

        String value = valueNode.asText();
        return switch (key) {
            case "NAME" -> value.toUpperCase(Locale.ROOT);
            case "EMAIL" -> value.toLowerCase(Locale.ROOT);
            case "AMOUNT" -> formatAmount(value);
            default -> value;
        };
    }

    private String formatAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            int scale = amount.scale();
            if (amount.precision() > MAX_AMOUNT_PRECISION
                    || scale < -MAX_AMOUNT_ABSOLUTE_SCALE
                    || scale > MAX_AMOUNT_ABSOLUTE_SCALE) {
                return "0.00";
            }
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException exception) {
            return "0.00";
        }
    }

    private record StoredIdempotencyRecord(String requestDigest, String responseBody) {
        private StoredIdempotencyRecord {
            Objects.requireNonNull(requestDigest, "requestDigest must not be null");
            Objects.requireNonNull(responseBody, "responseBody must not be null");
        }
    }

    private record ProcessedRecord(String id, String data) {
        private ProcessedRecord {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(data, "data must not be null");
        }
    }
}
