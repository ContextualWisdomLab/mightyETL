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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Creates new durable jobs from immutable failed or cancelled owner-scoped source evidence.
 *
 * <p>The client resupplies the complete bounded JSON payload because terminal rows deliberately
 * clear it. Replay validates the payload through the same record contract as ordinary intake and
 * requires its SHA-256 digest to equal the source digest before insertion. The terminal source is
 * never updated.</p>
 *
 * <p>A versioned principal-scoped replay-key hash is stored in the existing submission identity
 * column. The transaction-level request lock serializes one key within a principal namespace, and
 * the table unique constraint remains a second integrity boundary. Replay-created rows enter the
 * ordinary pending/worker lifecycle with immutable source, root, and bounded generation lineage.</p>
 */
@Service
public class EtlJobReplayService {

    /** Maximum number of replay generations retained by the first lineage contract. */
    public static final int MAXIMUM_REPLAY_GENERATION = 100;

    private static final String REPLAY_KEY_DOMAIN = "mightyetl:durable-job-replay:v1:";
    private static final String REPLAY_LOCK_DOMAIN = "mightyetl:durable-job-replay-lock:v1:";
    private static final int MAX_PRINCIPAL_SCOPE_CODE_POINTS = 512;
    private static final String KEY_VALUE_EXPRESSION = "[A-Za-z0-9._:-]{16,128}";
    private static final Pattern KEY_VALUE_PROFILE = Pattern.compile(KEY_VALUE_EXPRESSION);
    private static final Pattern KEY_STRUCTURED_FIELD_PROFILE = Pattern.compile(
            "\"(" + KEY_VALUE_EXPRESSION + ")\""
    );

    private static final String SELECT_EXISTING_REPLAY_SQL = """
            SELECT job_record_id, request_digest, job_status,
                   replay_source_job_record_id
            FROM etl_job_records
            WHERE principal_scope_hash = ?
              AND submission_key_hash = ?
            """;
    private static final String SELECT_REPLAY_SOURCE_SQL = """
            SELECT job_record_id, request_digest, job_status,
                   replay_root_job_record_id, replay_generation_count
            FROM etl_job_records
            WHERE job_record_id = ?
              AND principal_scope_hash = ?
            FOR UPDATE
            """;
    private static final String SELECT_OWNED_ROOT_COUNT_SQL = """
            SELECT COUNT(*)
            FROM etl_job_records
            WHERE job_record_id = ?
              AND principal_scope_hash = ?
            """;
    private static final String SELECT_LINEAGE_ROOT_COUNT_SQL = """
            SELECT COUNT(*)
            FROM etl_job_records
            WHERE job_record_id = ?
              AND principal_scope_hash = ?
              AND replay_source_job_record_id IS NULL
              AND replay_root_job_record_id IS NULL
              AND replay_generation_count IS NULL
            """;
    private static final String INSERT_REPLAY_JOB_SQL = """
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
            ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
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
                new PostgresEtlRequestLock(jdbcTemplate)
        );
    }

    /**
     * Creates replay admission with an explicit transaction-lifetime request lock.
     *
     * @param jdbcTemplate parameterized durable job persistence
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded request limits
     * @param requestLock transaction-lifetime replay-key lock
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
     * Creates or replays one new durable job from an immutable terminal source.
     *
     * @param sourceJobRecordId owner-scoped failed or cancelled source identifier
     * @param requestPayload resupplied exact bounded JSON array text
     * @param replayKey quoted Structured Field String or supported legacy raw safe value
     * @param principalScope authenticated principal namespace
     * @return new or previously-created replay job identity and current state
     * @throws NullPointerException when the source identifier is {@code null}
     * @throws EtlRequestException when validation, ownership, state, digest, key, or generation
     *     contracts fail
     * @throws IllegalStateException when no actual transaction is active or stored lineage is
     *     internally inconsistent
     */
    @Transactional
    public EtlJobReplay replayOwned(
            UUID sourceJobRecordId,
            @Nullable String requestPayload,
            @Nullable String replayKey,
            @Nullable String principalScope
    ) {
        UUID validatedSourceId = Objects.requireNonNull(
                sourceJobRecordId,
                "sourceJobRecordId must not be null"
        );
        String validatedKey = validateReplayKey(replayKey);
        String validatedScope = validatePrincipalScope(principalScope);
        String validatedPayload = validatePayload(requestPayload);
        requireActiveTransaction();

        String principalScopeHash = Sha256Digest.digest(validatedScope);
        String replayKeyHash = Sha256Digest.digest(
                REPLAY_KEY_DOMAIN + principalScopeHash + ':' + validatedKey
        );
        String replayLockHash = Sha256Digest.digest(
                REPLAY_LOCK_DOMAIN + principalScopeHash + ':' + replayKeyHash
        );
        String requestDigest = Sha256Digest.digest(validatedPayload);

        if (!requestLock.tryLock(replayLockHash)) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_IN_PROGRESS);
        }

        ExistingReplay existingReplay = findExistingReplay(principalScopeHash, replayKeyHash);
        if (existingReplay != null) {
            if (!validatedSourceId.equals(existingReplay.sourceJobRecordId())
                    || !requestDigest.equals(existingReplay.requestDigest())) {
                throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REUSED);
            }
            return new EtlJobReplay(
                    existingReplay.jobRecordId(),
                    existingReplay.jobStatus(),
                    true
            );
        }

        ReplaySource source = findSource(validatedSourceId, principalScopeHash);
        if (source == null) {
            throw new EtlRequestException(EtlRequestError.JOB_NOT_FOUND);
        }
        switch (source.jobStatus()) {
            case PENDING, RUNNING -> throw new EtlRequestException(
                    EtlRequestError.JOB_REPLAY_SOURCE_ACTIVE
            );
            case SUCCEEDED -> throw new EtlRequestException(
                    EtlRequestError.JOB_REPLAY_SOURCE_SUCCEEDED
            );
            case FAILED, CANCELLED -> {
                // These terminal outcomes are the only first-slice replay sources.
            }
        }
        if (!source.requestDigest().equals(requestDigest)) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_PAYLOAD_MISMATCH);
        }

        UUID rootJobRecordId;
        int replayGeneration;
        if (source.replayRootJobRecordId() == null) {
            if (source.replayGenerationCount() != null) {
                throw new IllegalStateException("Replay source has incomplete root lineage");
            }
            rootJobRecordId = source.jobRecordId();
            replayGeneration = 1;
        } else {
            Integer sourceGeneration = Objects.requireNonNull(
                    source.replayGenerationCount(),
                    "Replay source generation must accompany its root"
            );
            if (sourceGeneration >= MAXIMUM_REPLAY_GENERATION) {
                throw new EtlRequestException(
                        EtlRequestError.JOB_REPLAY_GENERATION_EXHAUSTED
                );
            }
            rootJobRecordId = source.replayRootJobRecordId();
            requireOwnedRoot(rootJobRecordId, principalScopeHash);
            replayGeneration = sourceGeneration + 1;
        }

        UUID newJobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                INSERT_REPLAY_JOB_SQL,
                newJobRecordId,
                principalScopeHash,
                replayKeyHash,
                requestDigest,
                validatedPayload,
                source.jobRecordId(),
                rootJobRecordId,
                replayGeneration
        );
        return new EtlJobReplay(newJobRecordId, EtlJobStatus.PENDING, false);
    }

    @Nullable
    private ExistingReplay findExistingReplay(
            String principalScopeHash,
            String replayKeyHash
    ) {
        List<ExistingReplay> rows = jdbcTemplate.query(
                SELECT_EXISTING_REPLAY_SQL,
                (resultSet, rowNumber) -> new ExistingReplay(
                        resultSet.getObject("job_record_id", UUID.class),
                        resultSet.getString("request_digest"),
                        EtlJobStatus.valueOf(resultSet.getString("job_status")),
                        resultSet.getObject("replay_source_job_record_id", UUID.class)
                ),
                principalScopeHash,
                replayKeyHash
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Nullable
    private ReplaySource findSource(UUID sourceJobRecordId, String principalScopeHash) {
        List<ReplaySource> rows = jdbcTemplate.query(
                SELECT_REPLAY_SOURCE_SQL,
                (resultSet, rowNumber) -> new ReplaySource(
                        resultSet.getObject("job_record_id", UUID.class),
                        resultSet.getString("request_digest"),
                        EtlJobStatus.valueOf(resultSet.getString("job_status")),
                        resultSet.getObject("replay_root_job_record_id", UUID.class),
                        resultSet.getObject("replay_generation_count", Integer.class)
                ),
                sourceJobRecordId,
                principalScopeHash
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void requireOwnedRoot(UUID rootJobRecordId, String principalScopeHash) {
        Integer ownedRootCount = jdbcTemplate.queryForObject(
                SELECT_OWNED_ROOT_COUNT_SQL,
                Integer.class,
                rootJobRecordId,
                principalScopeHash
        );
        if (!Integer.valueOf(1).equals(ownedRootCount)) {
            throw new IllegalStateException("Replay root is absent from the owner namespace");
        }

        Integer lineageRootCount = jdbcTemplate.queryForObject(
                SELECT_LINEAGE_ROOT_COUNT_SQL,
                Integer.class,
                rootJobRecordId,
                principalScopeHash
        );
        if (!Integer.valueOf(1).equals(lineageRootCount)) {
            throw new IllegalStateException("Replay root is not a lineage root");
        }
    }

    private String validatePayload(@Nullable String requestPayload) {
        if (requestPayload == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        if (requestPayload.getBytes(StandardCharsets.UTF_8).length
                > batchProperties.getMaxPayloadBytes()) {
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
            EtlJobService.validateRecord(record);
        }
        return requestPayload;
    }

    private static String validateReplayKey(@Nullable String replayKey) {
        if (replayKey == null) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
        }
        var structuredFieldMatcher = KEY_STRUCTURED_FIELD_PROFILE.matcher(replayKey);
        if (structuredFieldMatcher.matches()) {
            return structuredFieldMatcher.group(1);
        }
        if (KEY_VALUE_PROFILE.matcher(replayKey).matches()) {
            return replayKey;
        }
        throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
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
                    "Durable ETL job replay requires an active transaction"
            );
        }
    }

    private record ExistingReplay(
            UUID jobRecordId,
            String requestDigest,
            EtlJobStatus jobStatus,
            @Nullable UUID sourceJobRecordId
    ) {
        private ExistingReplay {
            Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
            Objects.requireNonNull(requestDigest, "requestDigest must not be null");
            Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        }
    }

    private record ReplaySource(
            UUID jobRecordId,
            String requestDigest,
            EtlJobStatus jobStatus,
            @Nullable UUID replayRootJobRecordId,
            @Nullable Integer replayGenerationCount
    ) {
        private ReplaySource {
            Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
            Objects.requireNonNull(requestDigest, "requestDigest must not be null");
            Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        }
    }
}
