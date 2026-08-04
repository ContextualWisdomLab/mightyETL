package com.xtrmetl.etl.job;

import java.util.Objects;
import java.util.UUID;

/**
 * Payload-bearing lease claim passed from the durable store to the transactional executor.
 *
 * @param jobRecordId stable opaque job identifier
 * @param principalScopeHash hashed owner namespace used for internal idempotency
 * @param requestPayload validated decoded JSON text
 * @param attemptCount number of committed claims including this lease
 * @param leaseOwnerId opaque token required for terminal or retry updates
 */
public record EtlJobClaim(
        UUID jobRecordId,
        String principalScopeHash,
        String requestPayload,
        int attemptCount,
        String leaseOwnerId
) {

    /**
     * Validates the complete claim needed for safe execution.
     */
    public EtlJobClaim {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(principalScopeHash, "principalScopeHash must not be null");
        Objects.requireNonNull(requestPayload, "requestPayload must not be null");
        Objects.requireNonNull(leaseOwnerId, "leaseOwnerId must not be null");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be at least one");
        }
    }
}
