package com.xtrmetl.etl.job;

import java.util.Optional;
import java.util.UUID;

/**
 * Durable persistence boundary for principal-scoped asynchronous ETL jobs.
 *
 * <p>Implementations must use atomic database statements for claim and lease-owner updates. A
 * terminal or retry update may succeed only for the current running lease owner.</p>
 */
public interface EtlJobStore {

    /**
     * Finds the durable submission identified by one owner and semantic client key.
     *
     * @param principalScopeHash hashed owner namespace
     * @param submissionKeyHash hashed owner-scoped semantic key
     * @return existing stored record when present
     */
    Optional<EtlJobRecord> findBySubmission(
            String principalScopeHash,
            String submissionKeyHash
    );

    /**
     * Inserts one validated pending job.
     *
     * @param jobRecordId new opaque identifier
     * @param principalScopeHash hashed owner namespace
     * @param submissionKeyHash hashed owner-scoped semantic key
     * @param requestDigest decoded JSON text digest
     * @param requestPayload validated decoded JSON text
     * @return inserted stored record
     */
    EtlJobRecord insertPending(
            UUID jobRecordId,
            String principalScopeHash,
            String submissionKeyHash,
            String requestDigest,
            String requestPayload
    );

    /**
     * Finds a job only when it belongs to the supplied owner namespace.
     *
     * @param jobRecordId opaque identifier
     * @param principalScopeHash hashed owner namespace
     * @return owned stored record when present
     */
    Optional<EtlJobRecord> findOwned(UUID jobRecordId, String principalScopeHash);

    /**
     * Claims the oldest pending or expired-running job without waiting on another consumer.
     *
     * @param leaseOwnerId new opaque lease token
     * @param leaseDurationSeconds positive lease duration
     * @return one claimed job when eligible work exists
     */
    Optional<EtlJobClaim> claimNext(String leaseOwnerId, int leaseDurationSeconds);

    /**
     * Commits successful terminal state for the current lease owner.
     *
     * @param jobRecordId opaque identifier
     * @param leaseOwnerId current lease token
     * @param responseBody successful ETL response
     * @throws IllegalStateException when the lease is no longer current
     */
    void markSucceeded(UUID jobRecordId, String leaseOwnerId, String responseBody);

    /**
     * Commits failed terminal state for the current lease owner.
     *
     * @param jobRecordId opaque identifier
     * @param leaseOwnerId current lease token
     * @param failureCode stable machine code
     * @throws IllegalStateException when the lease is no longer current
     */
    void markFailed(UUID jobRecordId, String leaseOwnerId, String failureCode);

    /**
     * Releases a retryable running job back to pending for the current lease owner.
     *
     * @param jobRecordId opaque identifier
     * @param leaseOwnerId current lease token
     * @throws IllegalStateException when the lease is no longer current
     */
    void releaseForRetry(UUID jobRecordId, String leaseOwnerId);
}
