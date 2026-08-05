package com.xtrmetl.etl.job;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Carries one immutable, fenced database claim into durable idempotent ETL execution.
 *
 * <p>The three lowercase SHA-256 values are non-reversible persistence identifiers copied from the
 * accepted job row. They let the worker reuse the durable response ledger without retaining or
 * reconstructing raw authenticated principals or raw client idempotency keys.</p>
 *
 * @param jobRecordId durable job identifier
 * @param leaseClaimId unique token generated for this exact claim or reclaim
 * @param leaseOwnerId non-sensitive process-lifetime worker identifier
 * @param principalScopeHash SHA-256 hash of the authenticated principal namespace
 * @param submissionKeyHash SHA-256 hash of the normalized durable submission key
 * @param requestDigest SHA-256 digest of the exact retained request payload
 * @param requestPayload validated JSON payload retained while the job is non-terminal
 * @param attemptCount one-based claim attempt count after this claim was persisted
 * @param leaseExpiresAt database-derived instant after which this claim is stale
 */
public record EtlJobLease(
        UUID jobRecordId,
        UUID leaseClaimId,
        String leaseOwnerId,
        String principalScopeHash,
        String submissionKeyHash,
        String requestDigest,
        String requestPayload,
        int attemptCount,
        Instant leaseExpiresAt
) {

    private static final Pattern SAFE_LEASE_OWNER_PATTERN = Pattern.compile(
            "[A-Za-z0-9._:-]{8,128}"
    );
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");

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
        requireSha256Hex(principalScopeHash, "principalScopeHash");
        requireSha256Hex(submissionKeyHash, "submissionKeyHash");
        requireSha256Hex(requestDigest, "requestDigest");
        Objects.requireNonNull(requestPayload, "requestPayload must not be null");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
    }

    private static void requireSha256Hex(String value, String fieldName) {
        String requiredValue = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!SHA256_HEX_PATTERN.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(
                    fieldName + " must be lowercase 64-character SHA-256 hex"
            );
        }
    }
}
