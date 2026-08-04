package com.xtrmetl.etl.job;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete durable job record used by the PostgreSQL adapter and application services.
 *
 * <p>This record is internal application data and must not be serialized directly to clients.
 * Use {@link #toView()} to remove hashes, payloads, and lease metadata.</p>
 *
 * @param jobRecordId stable opaque job identifier
 * @param principalScopeHash hashed owner namespace
 * @param submissionKeyHash hashed principal-scoped submission key
 * @param requestDigest hashed decoded JSON text
 * @param requestPayload request text retained only before a terminal state
 * @param jobStatus lifecycle status
 * @param attemptCount number of committed worker claims
 * @param leaseOwnerId current opaque lease token
 * @param leaseExpiresAt current lease expiry
 * @param responseBody successful ETL response
 * @param failureCode stable terminal failure code
 * @param submittedAt database submission time
 * @param startedAt first claim time
 * @param completedAt terminal completion time
 * @param updatedAt most recent database update time
 */
public record EtlJobRecord(
        UUID jobRecordId,
        String principalScopeHash,
        String submissionKeyHash,
        String requestDigest,
        @Nullable String requestPayload,
        EtlJobStatus jobStatus,
        int attemptCount,
        @Nullable String leaseOwnerId,
        @Nullable Instant leaseExpiresAt,
        @Nullable String responseBody,
        @Nullable String failureCode,
        Instant submittedAt,
        @Nullable Instant startedAt,
        @Nullable Instant completedAt,
        Instant updatedAt
) {

    /**
     * Validates required stored-record fields.
     */
    public EtlJobRecord {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(principalScopeHash, "principalScopeHash must not be null");
        Objects.requireNonNull(submissionKeyHash, "submissionKeyHash must not be null");
        Objects.requireNonNull(requestDigest, "requestDigest must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }

    /**
     * Removes storage-only and lease-only data for the authenticated API caller.
     *
     * @return immutable owner-safe representation
     */
    public EtlJobView toView() {
        return new EtlJobView(
                jobRecordId,
                jobStatus,
                "/api/etl/jobs/" + jobRecordId,
                attemptCount,
                submittedAt,
                startedAt,
                completedAt,
                responseBody,
                failureCode
        );
    }
}
