package com.xtrmetl.etl.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of the durable ETL job queue and lease store.
 *
 * <p>All values are bound as JDBC parameters. Claiming uses one data-modifying common table
 * expression with {@code FOR UPDATE SKIP LOCKED}; multiple replicas can therefore claim distinct
 * jobs without trusting scheduler-instance uniqueness.</p>
 */
@Component
public class PostgresEtlJobStore implements EtlJobStore {

    private static final String RECORD_COLUMNS = """
            job_record_id,
            principal_scope_hash,
            submission_key_hash,
            request_digest,
            request_payload,
            job_status,
            attempt_count,
            lease_owner_id,
            lease_expires_at,
            response_body,
            failure_code,
            submitted_at,
            started_at,
            completed_at,
            updated_at
            """;

    private static final String FIND_SUBMISSION_SQL = """
            SELECT %s
            FROM etl_job_records
            WHERE principal_scope_hash = ?
              AND submission_key_hash = ?
            """.formatted(RECORD_COLUMNS);

    private static final String INSERT_PENDING_SQL = """
            INSERT INTO etl_job_records (
                job_record_id,
                principal_scope_hash,
                submission_key_hash,
                request_digest,
                request_payload,
                job_status
            ) VALUES (?, ?, ?, ?, ?, 'pending')
            RETURNING %s
            """.formatted(RECORD_COLUMNS);

    private static final String FIND_OWNED_SQL = """
            SELECT %s
            FROM etl_job_records
            WHERE job_record_id = ?
              AND principal_scope_hash = ?
            """.formatted(RECORD_COLUMNS);

    private static final String CLAIM_NEXT_SQL = """
            WITH candidate_job AS (
                SELECT job_record_id
                FROM etl_job_records
                WHERE job_status = 'pending'
                   OR (
                        job_status = 'running'
                        AND lease_expires_at <= CURRENT_TIMESTAMP
                   )
                ORDER BY submitted_at ASC, job_record_id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE etl_job_records AS job_record
            SET job_status = 'running',
                attempt_count = job_record.attempt_count + 1,
                lease_owner_id = ?,
                lease_expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                started_at = COALESCE(job_record.started_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP
            FROM candidate_job
            WHERE job_record.job_record_id = candidate_job.job_record_id
            RETURNING
                job_record.job_record_id,
                job_record.principal_scope_hash,
                job_record.request_payload,
                job_record.attempt_count,
                job_record.lease_owner_id
            """;

    private static final String MARK_SUCCEEDED_SQL = """
            UPDATE etl_job_records
            SET job_status = 'succeeded',
                request_payload = NULL,
                response_body = ?,
                failure_code = NULL,
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                completed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND job_status = 'running'
              AND lease_owner_id = ?
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE etl_job_records
            SET job_status = 'failed',
                request_payload = NULL,
                response_body = NULL,
                failure_code = ?,
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                completed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND job_status = 'running'
              AND lease_owner_id = ?
            """;

    private static final String RELEASE_RETRY_SQL = """
            UPDATE etl_job_records
            SET job_status = 'pending',
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND job_status = 'running'
              AND lease_owner_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the PostgreSQL durable job adapter.
     *
     * @param jdbcTemplate parameterized database access
     */
    public PostgresEtlJobStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public Optional<EtlJobRecord> findBySubmission(
            String principalScopeHash,
            String submissionKeyHash
    ) {
        List<EtlJobRecord> records = jdbcTemplate.query(
                FIND_SUBMISSION_SQL,
                PostgresEtlJobStore::mapRecord,
                Objects.requireNonNull(principalScopeHash, "principalScopeHash must not be null"),
                Objects.requireNonNull(submissionKeyHash, "submissionKeyHash must not be null")
        );
        return first(records);
    }

    /** {@inheritDoc} */
    @Override
    public EtlJobRecord insertPending(
            UUID jobRecordId,
            String principalScopeHash,
            String submissionKeyHash,
            String requestDigest,
            String requestPayload
    ) {
        List<EtlJobRecord> records = jdbcTemplate.query(
                INSERT_PENDING_SQL,
                PostgresEtlJobStore::mapRecord,
                Objects.requireNonNull(jobRecordId, "jobRecordId must not be null"),
                Objects.requireNonNull(principalScopeHash, "principalScopeHash must not be null"),
                Objects.requireNonNull(submissionKeyHash, "submissionKeyHash must not be null"),
                Objects.requireNonNull(requestDigest, "requestDigest must not be null"),
                Objects.requireNonNull(requestPayload, "requestPayload must not be null")
        );
        if (records.size() != 1) {
            throw new IllegalStateException("Pending ETL job insert did not return exactly one row");
        }
        return records.getFirst();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<EtlJobRecord> findOwned(UUID jobRecordId, String principalScopeHash) {
        List<EtlJobRecord> records = jdbcTemplate.query(
                FIND_OWNED_SQL,
                PostgresEtlJobStore::mapRecord,
                Objects.requireNonNull(jobRecordId, "jobRecordId must not be null"),
                Objects.requireNonNull(principalScopeHash, "principalScopeHash must not be null")
        );
        return first(records);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<EtlJobClaim> claimNext(String leaseOwnerId, int leaseDurationSeconds) {
        String requiredLeaseOwner = Objects.requireNonNull(
                leaseOwnerId,
                "leaseOwnerId must not be null"
        );
        if (leaseDurationSeconds < 1) {
            throw new IllegalArgumentException("leaseDurationSeconds must be positive");
        }
        List<EtlJobClaim> claims = jdbcTemplate.query(
                CLAIM_NEXT_SQL,
                PostgresEtlJobStore::mapClaim,
                requiredLeaseOwner,
                leaseDurationSeconds
        );
        return first(claims);
    }

    /** {@inheritDoc} */
    @Override
    public void markSucceeded(UUID jobRecordId, String leaseOwnerId, String responseBody) {
        requireOneOwnedUpdate(
                jdbcTemplate.update(
                        MARK_SUCCEEDED_SQL,
                        Objects.requireNonNull(responseBody, "responseBody must not be null"),
                        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null"),
                        Objects.requireNonNull(leaseOwnerId, "leaseOwnerId must not be null")
                ),
                "succeed"
        );
    }

    /** {@inheritDoc} */
    @Override
    public void markFailed(UUID jobRecordId, String leaseOwnerId, String failureCode) {
        requireOneOwnedUpdate(
                jdbcTemplate.update(
                        MARK_FAILED_SQL,
                        Objects.requireNonNull(failureCode, "failureCode must not be null"),
                        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null"),
                        Objects.requireNonNull(leaseOwnerId, "leaseOwnerId must not be null")
                ),
                "fail"
        );
    }

    /** {@inheritDoc} */
    @Override
    public void releaseForRetry(UUID jobRecordId, String leaseOwnerId) {
        requireOneOwnedUpdate(
                jdbcTemplate.update(
                        RELEASE_RETRY_SQL,
                        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null"),
                        Objects.requireNonNull(leaseOwnerId, "leaseOwnerId must not be null")
                ),
                "release"
        );
    }

    private static EtlJobRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EtlJobRecord(
                resultSet.getObject("job_record_id", UUID.class),
                resultSet.getString("principal_scope_hash"),
                resultSet.getString("submission_key_hash"),
                resultSet.getString("request_digest"),
                resultSet.getString("request_payload"),
                EtlJobStatus.fromDatabase(resultSet.getString("job_status")),
                resultSet.getInt("attempt_count"),
                resultSet.getString("lease_owner_id"),
                instant(resultSet, "lease_expires_at"),
                resultSet.getString("response_body"),
                resultSet.getString("failure_code"),
                requiredInstant(resultSet, "submitted_at"),
                instant(resultSet, "started_at"),
                instant(resultSet, "completed_at"),
                requiredInstant(resultSet, "updated_at")
        );
    }

    private static EtlJobClaim mapClaim(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EtlJobClaim(
                resultSet.getObject("job_record_id", UUID.class),
                resultSet.getString("principal_scope_hash"),
                resultSet.getString("request_payload"),
                resultSet.getInt("attempt_count"),
                resultSet.getString("lease_owner_id")
        );
    }

    private static Instant requiredInstant(ResultSet resultSet, String column) throws SQLException {
        Instant value = instant(resultSet, column);
        if (value == null) {
            throw new SQLException(column + " must not be null");
        }
        return value;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static void requireOneOwnedUpdate(int updatedRows, String operation) {
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Current ETL job lease was lost before " + operation + " update"
            );
        }
    }
}
