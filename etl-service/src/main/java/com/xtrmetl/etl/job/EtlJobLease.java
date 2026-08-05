package com.xtrmetl.etl.job;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Carries one immutable, fenced database claim into ETL execution.
 *
 * @param jobRecordId durable job identifier
 * @param leaseClaimId unique token generated for this exact claim or reclaim
 * @param leaseOwnerId non-sensitive process-lifetime worker identifier
 * @param requestPayload validated JSON payload retained while the job is non-terminal
 * @param attemptCount one-based claim attempt count after this claim was persisted
 * @param leaseExpiresAt database-derived instant after which this claim is stale
 */
public record EtlJobLease(
        UUID jobRecordId,
        UUID leaseClaimId,
        String leaseOwnerId,
        String requestPayload,
        int attemptCount,
        Instant leaseExpiresAt
) {

    private static final Pattern SAFE_LEASE_OWNER_PATTERN = Pattern.compile(
            "[A-Za-z0-9._:-]{8,128}"
    );

    /**
     * Validates every field needed for exact lease fencing and deterministic execution.
     */
    public EtlJobLease {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(leaseClaimId, "leaseClaimId must not be null");
        String requiredOwnerId = Objects.requireNonNull(
                leaseOwnerId,
                "leaseOwnerId must not be null"
        );
        if (!SAFE_LEASE_OWNER_PATTERN.matcher(requiredOwnerId).matches()) {
            throw new IllegalArgumentException(
                    "leaseOwnerId must match [A-Za-z0-9._:-]{8,128}"
            );
        }
        Objects.requireNonNull(requestPayload, "requestPayload must not be null");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
    }
}
