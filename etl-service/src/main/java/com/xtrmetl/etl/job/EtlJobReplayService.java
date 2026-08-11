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

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admits owner-scoped durable-job replay requests on the repaired stack.
 *
 * <p>The service validates replay identity, authenticated principal scope, and JSON shape before
 * database work. The current persistence increment admits only an owner's first-generation replay
 * of a {@code FAILED} or {@code CANCELLED} source whose resupplied payload has the exact source
 * request digest. It classifies owner-safe absence, active and successful sources, unsupported
 * persisted states, and payload mismatch before insertion. Later test-first increments add
 * idempotent replay lookup, concurrency, and generation-depth policies before the HTTP replay
 * resource is exposed.</p>
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
    private static final String SELECT_FIRST_GENERATION_SOURCE_SQL = """
            SELECT job_record_id, request_digest, job_status
              FROM etl_job_records
             WHERE job_record_id = ?
               AND principal_scope_hash = ?
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
     * @param requestLock transaction-lifetime replay-key lock reserved for the idempotency increment
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
     * Creates one first-generation replay of an owner-scoped failed or cancelled durable job.
     *
     * <p>Authentication scope identifies the source row before its persisted state is classified,
     * preserving the same not-found result for missing and foreign-owned jobs. Only failed or
     * cancelled root jobs whose immutable request digest matches the resupplied payload proceed to
     * insertion. The source row is locked and never mutated. Replay-key normalization remains
     * domain-separated before reuse of the existing durable submission-key column.</p>
     *
     * @param sourceJobRecordId durable root job whose immutable intent is being replayed
     * @param requestBody bounded JSON-array payload resupplied by the authenticated owner
     * @param rawReplayKey principal-scoped idempotency key for this replay request
     * @param principalName authenticated principal namespace
     * @return newly created pending replay job
     * @throws NullPointerException when the source job identifier is absent
     * @throws EtlRequestException when key, principal, JSON, ownership, state, or payload validation fails
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
        List<ReplaySource> replaySources = jdbcTemplate.query(
                SELECT_FIRST_GENERATION_SOURCE_SQL,
                (resultSet, rowNumber) -> new ReplaySource(
                        resultSet.getObject("job_record_id", UUID.class),
                        resultSet.getString("request_digest"),
                        resultSet.getString("job_status")
                ),
                validatedSourceJobRecordId,
                principalScopeHash
        );
        if (replaySources.isEmpty()) {
            throw new EtlRequestException(EtlRequestError.JOB_NOT_FOUND);
        }

        ReplaySource source = replaySources.getFirst();
        switch (source.jobStatus()) {
            case "PENDING", "RUNNING" -> throw new EtlRequestException(
                    EtlRequestError.JOB_REPLAY_SOURCE_ACTIVE
            );
            case "SUCCEEDED" -> throw new EtlRequestException(
                    EtlRequestError.JOB_REPLAY_SOURCE_SUCCEEDED
            );
            case "FAILED", "CANCELLED" -> {
                // These terminal outcomes are the bounded first-generation replay sources.
            }
            default -> throw new EtlRequestException(
                    EtlRequestError.JOB_REPLAY_SOURCE_UNSUPPORTED
            );
        }
        if (!requestDigest.equals(source.requestDigest())) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_PAYLOAD_MISMATCH);
        }

        UUID replayJobRecordId = UUID.randomUUID();
        String replayKeyHash = Sha256Digest.digest(
                REPLAY_KEY_HASH_DOMAIN + principalScopeHash + ":" + validatedReplayKey
        );
        jdbcTemplate.update(
                INSERT_FIRST_GENERATION_REPLAY_SQL,
                replayJobRecordId,
                principalScopeHash,
                replayKeyHash,
                requestDigest,
                requestBody,
                source.jobRecordId(),
                source.jobRecordId()
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
        if (requestBody == null || requestBody.isEmpty()) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            if (root == null || !root.isArray()) {
                throw new EtlRequestException(EtlRequestError.INVALID_JSON);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON, exception);
        }
    }

    private record ReplaySource(UUID jobRecordId, String requestDigest, String jobStatus) {
    }
}
