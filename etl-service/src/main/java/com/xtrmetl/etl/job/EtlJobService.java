package com.xtrmetl.etl.job;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.PostgresEtlRequestLock;
import com.xtrmetl.etl.service.Sha256Digest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Creates and reads durable principal-scoped asynchronous ETL job resources.
 *
 * <p>The intake path validates the complete bounded JSON batch before persistence. Raw
 * authentication principals and idempotency keys are never stored. Instead, independent SHA-256
 * hashes namespace the submission, while the exact JSON text is retained only because a later
 * worker slice must execute the accepted job. The status representation never exposes that payload
 * or either internal hash.</p>
 *
 * <p>A PostgreSQL transaction-level try-lock serializes creation of one principal-scoped
 * submission key. The table-level unique constraint remains a second integrity boundary. Replaying
 * byte-identical JSON returns the original job identifier; reusing the key with different JSON text
 * returns a deterministic conflict.</p>
 */
@Service
public class EtlJobService {

    private static final String INSERT_JOB_SQL = """
            INSERT INTO etl_job_records (
                job_record_id,
                principal_scope_hash,
                submission_key_hash,
                request_digest,
                request_payload,
                job_status,
                attempt_count
            ) VALUES (?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SELECT_SUBMISSION_SQL = """
            SELECT job_record_id, request_digest, job_status, attempt_count,
                   failure_code, created_at, updated_at
            FROM etl_job_records
            WHERE principal_scope_hash = ?
              AND submission_key_hash = ?
            """;
    private static final String SELECT_OWNED_JOB_SQL = """
            SELECT job_record_id, request_digest, job_status, attempt_count,
                   failure_code, created_at, updated_at
            FROM etl_job_records
            WHERE job_record_id = ?
              AND principal_scope_hash = ?
            """;
    private static final int MAX_PRINCIPAL_SCOPE_CODE_POINTS = 512;
    private static final int MAX_RECORD_ID_CODE_POINTS = 256;
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
     * Creates the job service with the PostgreSQL transaction-lock adapter.
     *
     * <p>This overload supports direct construction while Spring production wiring uses the
     * four-argument constructor.</p>
     *
     * @param jdbcTemplate parameterized database access
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded ETL request limits
     */
    public EtlJobService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EtlBatchProperties batchProperties
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                batchProperties,
                new PostgresEtlRequestLock(jdbcTemplate)
        );
    }

    /**
     * Creates the job service with an explicit transaction-lifetime request lock.
     *
     * @param jdbcTemplate parameterized database access
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded ETL request limits
     * @param requestLock transaction-lifetime submission lock
     */
    @Autowired
    public EtlJobService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EtlBatchProperties batchProperties,
            EtlRequestLock requestLock
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        ObjectMapper sourceMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
        this.objectMapper = sourceMapper.copy();
        this.objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.batchProperties = Objects.requireNonNull(
                batchProperties,
                "batchProperties must not be null"
        );
        this.requestLock = Objects.requireNonNull(requestLock, "requestLock must not be null");
    }

    /**
     * Creates or replays one durable job submission.
     *
     * <p>The complete operation runs in one transaction. Validation and configured admission
     * limits execute before request-lock or table access. A quoted RFC 9651 String and the retained
     * legacy raw representation normalize to the same semantic idempotency key.</p>
     *
     * @param requestPayload exact UTF-8 JSON array text retained until terminal processing
     * @param idempotencyKey required quoted Structured Field String or legacy raw safe value
     * @param principalScope authenticated principal namespace
     * @return new or replayed durable job identity
     * @throws EtlRequestException when validation, ownership, or submission-key contracts fail
     * @throws IllegalStateException when invoked without an actual transaction
     */
    @Transactional
    public EtlJobSubmission submit(
            @Nullable String requestPayload,
            @Nullable String idempotencyKey,
            @Nullable String principalScope
    ) {
        String validatedKey = validateIdempotencyKey(idempotencyKey);
        String validatedScope = validatePrincipalScope(principalScope);
        String validatedPayload = validatePayload(requestPayload);
        requireActiveTransaction();

        String principalScopeHash = Sha256Digest.digest(validatedScope);
        String submissionKeyHash = Sha256Digest.digest(validatedKey);
        String submissionLockHash = Sha256Digest.digest(
                principalScopeHash + ":" + submissionKeyHash
        );
        String requestDigest = Sha256Digest.digest(validatedPayload);

        if (!requestLock.tryLock(submissionLockHash)) {
            throw new EtlRequestException(EtlRequestError.JOB_SUBMISSION_IN_PROGRESS);
        }

        StoredJobRecord storedJob = findSubmission(principalScopeHash, submissionKeyHash);
        if (storedJob != null) {
            if (!storedJob.requestDigest().equals(requestDigest)) {
                throw new EtlRequestException(EtlRequestError.JOB_SUBMISSION_KEY_REUSED);
            }
            return new EtlJobSubmission(
                    storedJob.snapshot().jobRecordId(),
                    storedJob.snapshot().jobStatus(),
                    true
            );
        }

        UUID jobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                INSERT_JOB_SQL,
                jobRecordId,
                principalScopeHash,
                submissionKeyHash,
                requestDigest,
                validatedPayload,
                EtlJobStatus.PENDING.name()
        );
        return new EtlJobSubmission(jobRecordId, EtlJobStatus.PENDING, false);
    }

    /**
     * Returns one job only when it belongs to the authenticated principal.
     *
     * <p>Missing identifiers and identifiers owned by another principal intentionally produce the
     * same stable not-found error, preventing cross-tenant existence probing.</p>
     *
     * @param jobRecordId opaque job identifier from the submission response
     * @param principalScope authenticated principal namespace
     * @return operator-safe job status snapshot
     * @throws EtlRequestException when the job is absent from the principal namespace
     */
    @Transactional(readOnly = true)
    public EtlJobSnapshot findOwned(
            UUID jobRecordId,
            @Nullable String principalScope
    ) {
        UUID validatedJobId = Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        String principalScopeHash = Sha256Digest.digest(validatePrincipalScope(principalScope));
        List<EtlJobSnapshot> jobs = jdbcTemplate.query(
                SELECT_OWNED_JOB_SQL,
                (resultSet, rowNumber) -> mapSnapshot(resultSet.getObject("job_record_id", UUID.class),
                        resultSet.getString("job_status"),
                        resultSet.getInt("attempt_count"),
                        resultSet.getString("failure_code"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at")),
                validatedJobId,
                principalScopeHash
        );
        if (jobs.isEmpty()) {
            throw new EtlRequestException(EtlRequestError.JOB_NOT_FOUND);
        }
        return jobs.getFirst();
    }

    private StoredJobRecord findSubmission(String principalScopeHash, String submissionKeyHash) {
        List<StoredJobRecord> jobs = jdbcTemplate.query(
                SELECT_SUBMISSION_SQL,
                (resultSet, rowNumber) -> new StoredJobRecord(
                        resultSet.getString("request_digest"),
                        mapSnapshot(
                                resultSet.getObject("job_record_id", UUID.class),
                                resultSet.getString("job_status"),
                                resultSet.getInt("attempt_count"),
                                resultSet.getString("failure_code"),
                                resultSet.getTimestamp("created_at"),
                                resultSet.getTimestamp("updated_at")
                        )
                ),
                principalScopeHash,
                submissionKeyHash
        );
        return jobs.isEmpty() ? null : jobs.getFirst();
    }

    private static EtlJobSnapshot mapSnapshot(
            UUID jobRecordId,
            String jobStatus,
            int attemptCount,
            @Nullable String failureCode,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
        Instant createdInstant = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        ).toInstant();
        Instant updatedInstant = Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null"
        ).toInstant();
        return new EtlJobSnapshot(
                jobRecordId,
                EtlJobStatus.valueOf(jobStatus),
                attemptCount,
                failureCode,
                createdInstant,
                updatedInstant
        );
    }

    private String validatePayload(@Nullable String requestPayload) {
        if (requestPayload == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        int payloadBytes = requestPayload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > batchProperties.getMaxPayloadBytes()) {
            throw new EtlRequestException(EtlRequestError.PAYLOAD_TOO_LARGE);
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(requestPayload);
        } catch (JsonProcessingException exception) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON, exception);
        }
        if (root == null || root.isNull() || !root.isArray()) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        if (root.size() > batchProperties.getMaxBatchRecords()) {
            throw new EtlRequestException(EtlRequestError.BATCH_TOO_LARGE);
        }
        for (JsonNode record : root) {
            validateRecord(record);
        }
        return requestPayload;
    }

    static void validateRecord(@Nullable JsonNode record) {
        if (record == null || !record.isObject()) {
            throw new EtlRequestException(EtlRequestError.INVALID_RECORD);
        }
        JsonNode idNode = record.get("id");
        if (idNode == null || !idNode.isTextual()) {
            throw new EtlRequestException(EtlRequestError.INVALID_RECORD);
        }
        String identifier = idNode.asText();
        int codePointCount = identifier.codePointCount(0, identifier.length());
        boolean hasUnsafeCodePoint = identifier.codePoints()
                .anyMatch(EtlJobService::isUnsafeIdentifierCodePoint);
        boolean hasOuterWhitespace = OUTER_IDENTIFIER_WHITESPACE.matcher(identifier).find();
        if (identifier.isBlank()
                || !identifier.equals(identifier.strip())
                || hasOuterWhitespace
                || codePointCount > MAX_RECORD_ID_CODE_POINTS
                || hasUnsafeCodePoint) {
            throw new EtlRequestException(EtlRequestError.INVALID_RECORD);
        }

        Set<String> normalizedKeys = new HashSet<>();
        record.properties().forEach(field -> {
            String normalizedKey = field.getKey().toUpperCase(Locale.ROOT);
            if (!normalizedKeys.add(normalizedKey)) {
                throw new EtlRequestException(EtlRequestError.INVALID_RECORD);
            }
        });
    }

    private static boolean isUnsafeIdentifierCodePoint(int codePoint) {
        int characterType = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || characterType == Character.FORMAT
                || characterType == Character.LINE_SEPARATOR
                || characterType == Character.PARAGRAPH_SEPARATOR;
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
                    "Durable ETL job submission requires an active transaction"
            );
        }
    }

    private record StoredJobRecord(String requestDigest, EtlJobSnapshot snapshot) {
        private StoredJobRecord {
            Objects.requireNonNull(requestDigest, "requestDigest must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }
    }
}
