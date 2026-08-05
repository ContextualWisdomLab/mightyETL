package com.xtrmetl.etl.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns PostgreSQL-backed durable-job claims and exact lease-fenced state transitions.
 *
 * <p>Every claim transaction first terminalizes eligible exhausted rows, then locks at most one
 * oldest eligible row with {@code FOR UPDATE SKIP LOCKED}, and finally writes a fresh claim token,
 * owner, expiry, and incremented attempt count before commit. State transitions repeat the exact
 * claim token, owner, running status, and database-time expiry predicates so stale workers cannot
 * mutate lifecycle state.</p>
 */
@Repository
public class EtlJobLeaseRepository {

    /** Stable terminal code assigned when no additional claim is permitted. */
    public static final String ATTEMPTS_EXHAUSTED_FAILURE_CODE =
            "etl_worker_attempts_exhausted";

    private static final Pattern SAFE_OWNER_PATTERN = Pattern.compile(
            "[A-Za-z0-9._:-]{8,128}"
    );
    private static final Pattern SAFE_FAILURE_CODE_PATTERN = Pattern.compile(
            "[a-z][a-z0-9_]{2,127}"
    );

    private static final String TERMINALIZE_EXHAUSTED_SQL = """
            UPDATE etl_job_records
            SET job_status = 'FAILED',
                request_payload = NULL,
                failure_code = ?,
                lease_claim_id = NULL,
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE attempt_count >= ?
              AND (
                  job_status = 'PENDING'
                  OR (
                      job_status = 'RUNNING'
                      AND lease_expires_at <= CURRENT_TIMESTAMP
                  )
              )
            """;

    private static final String SELECT_CANDIDATE_SQL = """
            SELECT job_record_id,
                   principal_scope_hash,
                   submission_key_hash,
                   request_digest,
                   request_payload,
                   attempt_count,
                   CURRENT_TIMESTAMP AS database_now
            FROM etl_job_records
            WHERE attempt_count < ?
              AND (
                  job_status = 'PENDING'
                  OR (
                      job_status = 'RUNNING'
                      AND lease_expires_at <= CURRENT_TIMESTAMP
                  )
              )
            ORDER BY created_at, job_record_id
            FETCH FIRST 1 ROW ONLY
            FOR UPDATE SKIP LOCKED
            """;

    private static final String CLAIM_CANDIDATE_SQL = """
            UPDATE etl_job_records
            SET job_status = 'RUNNING',
                attempt_count = attempt_count + 1,
                failure_code = NULL,
                lease_claim_id = ?,
                lease_owner_id = ?,
                lease_expires_at = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND attempt_count = ?
              AND (
                  job_status = 'PENDING'
                  OR (
                      job_status = 'RUNNING'
                      AND lease_expires_at <= CURRENT_TIMESTAMP
                  )
              )
            """;

    private static final String MARK_SUCCEEDED_SQL = """
            UPDATE etl_job_records
            SET job_status = 'SUCCEEDED',
                request_payload = NULL,
                failure_code = NULL,
                lease_claim_id = NULL,
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND job_status = 'RUNNING'
              AND lease_claim_id = ?
              AND lease_owner_id = ?
              AND lease_expires_at > CURRENT_TIMESTAMP
            """;

    private static final String RELEASE_FOR_RETRY_SQL = """
            UPDATE etl_job_records
            SET job_status = 'PENDING',
                failure_code = NULL,
                lease_claim_id = NULL,
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND job_status = 'RUNNING'
              AND lease_claim_id = ?
              AND lease_owner_id = ?
              AND lease_expires_at > CURRENT_TIMESTAMP
              AND attempt_count < ?
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE etl_job_records
            SET job_status = 'FAILED',
                request_payload = NULL,
                failure_code = ?,
                lease_claim_id = NULL,
                lease_owner_id = NULL,
                lease_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE job_record_id = ?
              AND job_status = 'RUNNING'
              AND lease_claim_id = ?
              AND lease_owner_id = ?
              AND lease_expires_at > CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates lease persistence using one JDBC adapter and one transaction authority.
     *
     * @param jdbcTemplate JDBC operations for the durable-job table
     * @param transactionManager transaction manager that owns row locks and claim commits
     */
    public EtlJobLeaseRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
        );
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(
                transactionManager,
                "transactionManager must not be null"
        ));
    }

    /**
     * Claims at most one oldest eligible job for one worker process.
     *
     * @param leaseOwnerId safe non-sensitive process identifier
     * @param leaseDuration positive duration applied to database claim time
     * @param maxAttempts maximum permitted claim count from 1 through 100
     * @return a fresh claim, or an empty result when no row is eligible
     * @throws NullPointerException when an argument is {@code null}
     * @throws IllegalArgumentException when an argument violates its bounded contract
     * @throws IllegalStateException when a locked candidate unexpectedly cannot be claimed
     */
    public Optional<EtlJobLease> claimNext(
            String leaseOwnerId,
            Duration leaseDuration,
            int maxAttempts
    ) {
        String validatedOwnerId = requireSafeOwnerId(leaseOwnerId);
        Duration validatedDuration = requirePositiveDuration(leaseDuration);
        int validatedMaxAttempts = requireMaxAttempts(maxAttempts);

        return Objects.requireNonNull(transactionTemplate.execute(transactionStatus -> {
            jdbcTemplate.update(
                    TERMINALIZE_EXHAUSTED_SQL,
                    ATTEMPTS_EXHAUSTED_FAILURE_CODE,
                    validatedMaxAttempts
            );
            List<ClaimCandidate> candidates = jdbcTemplate.query(
                    SELECT_CANDIDATE_SQL,
                    (resultSet, rowNumber) -> new ClaimCandidate(
                            resultSet.getObject("job_record_id", UUID.class),
                            resultSet.getString("principal_scope_hash"),
                            resultSet.getString("submission_key_hash"),
                            resultSet.getString("request_digest"),
                            resultSet.getString("request_payload"),
                            resultSet.getInt("attempt_count"),
                            resultSet.getObject("database_now", OffsetDateTime.class).toInstant()
                    ),
                    validatedMaxAttempts
            );
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            ClaimCandidate candidate = candidates.getFirst();
            UUID leaseClaimId = UUID.randomUUID();
            Instant leaseExpiresAt = candidate.databaseNow().plus(validatedDuration);
            int updatedRows = jdbcTemplate.update(
                    CLAIM_CANDIDATE_SQL,
                    leaseClaimId,
                    validatedOwnerId,
                    OffsetDateTime.ofInstant(leaseExpiresAt, ZoneOffset.UTC),
                    candidate.jobRecordId(),
                    candidate.attemptCount()
            );
            if (updatedRows != 1) {
                throw new IllegalStateException("Locked ETL job candidate could not be claimed");
            }
            return Optional.of(new EtlJobLease(
                    candidate.jobRecordId(),
                    leaseClaimId,
                    validatedOwnerId,
                    candidate.principalScopeHash(),
                    candidate.submissionKeyHash(),
                    candidate.requestDigest(),
                    candidate.requestPayload(),
                    candidate.attemptCount() + 1,
                    leaseExpiresAt
            ));
        }), "claim transaction must return a result");
    }

    /**
     * Commits terminal success only for the exact live claim.
     *
     * @param lease exact claim whose target effects completed in the same transaction
     * @throws NullPointerException when the lease is {@code null}
     * @throws StaleEtlJobLeaseException when the claim is expired or no longer authoritative
     */
    public void markSucceeded(EtlJobLease lease) {
        EtlJobLease requiredLease = Objects.requireNonNull(lease, "lease must not be null");
        requireTransition(jdbcTemplate.update(
                MARK_SUCCEEDED_SQL,
                requiredLease.jobRecordId(),
                requiredLease.leaseClaimId(),
                requiredLease.leaseOwnerId()
        ));
    }

    /**
     * Returns a failed execution to pending only while attempts remain and the claim is exact.
     *
     * @param lease exact live claim to release
     * @param maxAttempts maximum permitted claim count from 1 through 100
     * @throws NullPointerException when the lease is {@code null}
     * @throws IllegalArgumentException when the maximum is outside the supported range
     * @throws StaleEtlJobLeaseException when the claim is stale or no retry remains
     */
    public void releaseForRetry(EtlJobLease lease, int maxAttempts) {
        EtlJobLease requiredLease = Objects.requireNonNull(lease, "lease must not be null");
        int validatedMaxAttempts = requireMaxAttempts(maxAttempts);
        requireTransition(jdbcTemplate.update(
                RELEASE_FOR_RETRY_SQL,
                requiredLease.jobRecordId(),
                requiredLease.leaseClaimId(),
                requiredLease.leaseOwnerId(),
                validatedMaxAttempts
        ));
    }

    /**
     * Commits terminal failure and clears the retained payload for the exact live claim.
     *
     * @param lease exact live claim to fail
     * @param failureCode stable non-sensitive machine-readable failure classification
     * @throws NullPointerException when an argument is {@code null}
     * @throws IllegalArgumentException when the failure code is unsafe
     * @throws StaleEtlJobLeaseException when the claim is expired or no longer authoritative
     */
    public void markFailed(EtlJobLease lease, String failureCode) {
        EtlJobLease requiredLease = Objects.requireNonNull(lease, "lease must not be null");
        String validatedFailureCode = requireSafeFailureCode(failureCode);
        requireTransition(jdbcTemplate.update(
                MARK_FAILED_SQL,
                validatedFailureCode,
                requiredLease.jobRecordId(),
                requiredLease.leaseClaimId(),
                requiredLease.leaseOwnerId()
        ));
    }

    private static String requireSafeOwnerId(String leaseOwnerId) {
        String requiredOwnerId = Objects.requireNonNull(
                leaseOwnerId,
                "leaseOwnerId must not be null"
        );
        if (!SAFE_OWNER_PATTERN.matcher(requiredOwnerId).matches()) {
            throw new IllegalArgumentException(
                    "leaseOwnerId must match [A-Za-z0-9._:-]{8,128}"
            );
        }
        return requiredOwnerId;
    }

    private static Duration requirePositiveDuration(Duration leaseDuration) {
        Duration requiredDuration = Objects.requireNonNull(
                leaseDuration,
                "leaseDuration must not be null"
        );
        if (requiredDuration.isZero() || requiredDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        return requiredDuration;
    }

    private static int requireMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
        }
        return maxAttempts;
    }

    private static String requireSafeFailureCode(String failureCode) {
        String requiredFailureCode = Objects.requireNonNull(
                failureCode,
                "failureCode must not be null"
        );
        if (!SAFE_FAILURE_CODE_PATTERN.matcher(requiredFailureCode).matches()) {
            throw new IllegalArgumentException(
                    "failureCode must match [a-z][a-z0-9_]{2,127}"
            );
        }
        return requiredFailureCode;
    }

    private static void requireTransition(int updatedRows) {
        if (updatedRows != 1) {
            throw new StaleEtlJobLeaseException();
        }
    }

    private record ClaimCandidate(
            UUID jobRecordId,
            String principalScopeHash,
            String submissionKeyHash,
            String requestDigest,
            String requestPayload,
            int attemptCount,
            Instant databaseNow
    ) {
    }
}
