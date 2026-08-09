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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admits owner-scoped durable-job replay requests on the repaired stack.
 *
 * <p>The service validates replay identity, authenticated principal scope, and JSON shape before
 * database work. The first persistence increments admit an owner's first-generation replay of a
 * {@code FAILED} or {@code CANCELLED} source, serialize creation by principal-scoped replay key,
 * return the already-created job for an identical owner/source/key/payload replay, and reject a
 * committed replay key that identifies a different source or payload. Later test-first increments
 * add source-state classification and generation-depth policies before the HTTP replay resource is
 * exposed.</p>
 */
@Service
public class EtlJobReplayService {

    private static final int MAX_PRINCIPAL_SCOPE_CODE_POINTS = 512;
    private static final String IDEMPOTENCY_KEY_VALUE_EXPRESSION = "[A-Za-z0-9._:-]{16,128}";
    private static final Pattern IDEMPOTENCY_KEY_VALUE_PROFILE = Pattern.compile(
            IDEMPOTENCY_KEY_VALUE_EXPRESSION
    );
    private static final Pattern IDEMPOTENCY_KEY_STRUCTURED_FIELD_PROFILE = Pattern.compile(
            "\"(" + IDEMPOTENCY_KEY_VALUE_EXPRESSION + ")\""
    );
    private static final String REPLAY_KEY_HASH_DOMAIN = "mightyetl:durable-job-replay-key:v1:";
    private static final String REPLAY_LOCK_HASH_DOMAIN = "mightyetl:durable-job-replay-lock:v1:";
    private static final String SELECT_EXISTING_REPLAY_SQL = """
            SELECT job_record_id, request_digest, job_status, replay_source_job_record_id
              FROM etl_job_records
             WHERE principal_scope_hash = ?
               AND submission_key_hash = ?
            """;
    private static final String SELECT_FIRST_GENERATION_TERMINAL_SOURCE_SQL = """
            SELECT job_record_id
              FROM etl_job_records
             WHERE job_record_id = ?
               AND principal_scope_hash = ?
               AND request_digest = ?
               AND job_status IN ('FAILED', 'CANCELLED')
               AND replay_source_job_record_id IS NULL
               AND replay_root_job_record_id IS NULL
               AND replay_generation_count IS NULL
             FOR UPDATE
            """;
    private static final String INSERT_FIRST_GENERATION_REPLAY_SQL = """
            INSERT INTO etl_job_records (
                job_record_id,
                principal_scope_hash,
                submission_key_hash,
                request_digest,
                request_payload,
                job_status,
                attempt_count,
                replay_source_job_record_id,
                replay_root_job_record_id,
                replay_generation_count
            ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, 1)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EtlBatchProperties batchProperties;
    private final EtlRequestLock requestLock;

    /**
     * Creates replay admission with the PostgreSQL transaction-lock implementation.
     *
     * @param jdbcTemplate parameterized durable job persistence
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded request limits
     */
    public EtlJobReplayService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EtlBatchProperties batchProperties
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                batchProperties,
                new PostgresEtlRequestLock(Objects.requireNonNull(
                        jdbcTemplate,
                        "jdbcTemplate must not be null"
                ))
        );
    }

    /**
     * Creates replay admission with an explicit transaction-lifetime request lock.
     *
     * @param jdbcTemplate parameterized durable job persistence
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded request limits
     * @param requestLock transaction-lifetime principal-scoped replay-key lock
     */
    @Autowired
    public EtlJobReplayService(
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
     * Creates or returns one first-generation replay of an owner-scoped terminal durable job.
     *
     * <p>The principal-scoped replay identity is serialized before table access. An identical
     * committed replay is then looked up by owner/key/source/payload identity and returned without
     * a second insert. A committed key bound to another source or payload is rejected with
     * {@link EtlRequestError#JOB_REPLAY_KEY_REUSED}. A new replay locks the immutable terminal
     * source before inserting a fresh {@code PENDING} child.</p>
     *
     * @param sourceJobRecordId terminal durable job whose intent is being replayed
     * @param requestBody bounded JSON-array payload resupplied by the authenticated owner
     * @param rawReplayKey principal-scoped idempotency key for this replay request
     * @param principalName authenticated principal namespace
     * @return newly created or previously committed replay job
     * @throws NullPointerException when the source job identifier is absent
     * @throws EtlRequestException when validation fails, the key is busy, or intent conflicts
     */
    @Transactional
    public EtlJobReplay replayOwned(
            UUID sourceJobRecordId,
            @Nullable String requestBody,
            @Nullable String rawReplayKey,
            @Nullable String principalName
    ) {
        UUID validatedSourceJobRecordId = Objects.requireNonNull(
                sourceJobRecordId,
                "sourceJobRecordId must not be null"
        );
        String validatedReplayKey = validateReplayKey(rawReplayKey);
        String validatedPrincipalName = validatePrincipalScope(principalName);
        validateReplayPayload(requestBody);

        String principalScopeHash = Sha256Digest.digest(validatedPrincipalName);
        String requestDigest = Sha256Digest.digest(requestBody);
        String replayKeyHash = Sha256Digest.digest(
                REPLAY_KEY_HASH_DOMAIN + principalScopeHash + ":" + validatedReplayKey
        );
        String replayLockHash = Sha256Digest.digest(
                REPLAY_LOCK_HASH_DOMAIN + principalScopeHash + ":" + replayKeyHash
        );
        if (!requestLock.tryLock(replayLockHash)) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_IN_PROGRESS);
        }

        List<EtlJobReplay> existingReplays = jdbcTemplate.query(
                SELECT_EXISTING_REPLAY_SQL,
                (resultSet, rowNumber) -> {
                    UUID existingSourceJobRecordId = resultSet.getObject(
                            "replay_source_job_record_id",
                            UUID.class
                    );
                    String existingRequestDigest = resultSet.getString("request_digest");
                    if (!validatedSourceJobRecordId.equals(existingSourceJobRecordId)
                            || !requestDigest.equals(existingRequestDigest)) {
                        throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REUSED);
                    }
                    return new EtlJobReplay(
                            resultSet.getObject("job_record_id", UUID.class),
                            EtlJobStatus.valueOf(resultSet.getString("job_status")),
                            true
                    );
                },
                principalScopeHash,
                replayKeyHash
        );
        if (!existingReplays.isEmpty()) {
            return existingReplays.getFirst();
        }

        UUID lockedSourceJobRecordId = jdbcTemplate.queryForObject(
                SELECT_FIRST_GENERATION_TERMINAL_SOURCE_SQL,
                UUID.class,
                validatedSourceJobRecordId,
                principalScopeHash,
                requestDigest
        );
        UUID replayJobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                INSERT_FIRST_GENERATION_REPLAY_SQL,
                replayJobRecordId,
                principalScopeHash,
                replayKeyHash,
                requestDigest,
                requestBody,
                lockedSourceJobRecordId,
                lockedSourceJobRecordId
        );
        return new EtlJobReplay(replayJobRecordId, EtlJobStatus.PENDING, false);
    }

    private static String validateReplayKey(@Nullable String rawReplayKey) {
        if (rawReplayKey == null) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
        }
        var structuredFieldMatcher = IDEMPOTENCY_KEY_STRUCTURED_FIELD_PROFILE.matcher(rawReplayKey);
        if (structuredFieldMatcher.matches()) {
            return structuredFieldMatcher.group(1);
        }
        if (IDEMPOTENCY_KEY_VALUE_PROFILE.matcher(rawReplayKey).matches()) {
            return rawReplayKey;
        }
        throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
    }

    private static String validatePrincipalScope(@Nullable String principalName) {
        if (principalName == null
                || principalName.isBlank()
                || principalName.codePointCount(0, principalName.length())
                > MAX_PRINCIPAL_SCOPE_CODE_POINTS) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }
        return principalName;
    }

    private JsonNode validateReplayPayload(@Nullable String requestBody) {
        if (requestBody == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        int payloadBytes = requestBody.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > batchProperties.getMaxPayloadBytes()) {
            throw new EtlRequestException(EtlRequestError.PAYLOAD_TOO_LARGE);
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(requestBody);
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
            EtlJobService.validateRecord(record);
        }
        return root;
    }
}
