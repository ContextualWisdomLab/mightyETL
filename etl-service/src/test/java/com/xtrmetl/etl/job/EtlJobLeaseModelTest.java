package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Specifies the immutable value contract carried from a database claim into execution.
 */
class EtlJobLeaseModelTest {

    private static final UUID JOB_RECORD_ID = UUID.randomUUID();
    private static final UUID LEASE_CLAIM_ID = UUID.randomUUID();
    private static final String OWNER_ID = "worker-alpha";
    private static final String PRINCIPAL_SCOPE_HASH = "a".repeat(64);
    private static final String SUBMISSION_KEY_HASH = "b".repeat(64);
    private static final String REQUEST_DIGEST = "c".repeat(64);
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final Instant EXPIRY = Instant.parse("2026-08-05T01:00:00Z");

    @Test
    void retainsEveryValidatedClaimField() {
        EtlJobLease lease = validLease();

        assertEquals(JOB_RECORD_ID, lease.jobRecordId());
        assertEquals(LEASE_CLAIM_ID, lease.leaseClaimId());
        assertEquals(OWNER_ID, lease.leaseOwnerId());
        assertEquals(PRINCIPAL_SCOPE_HASH, lease.principalScopeHash());
        assertEquals(SUBMISSION_KEY_HASH, lease.submissionKeyHash());
        assertEquals(REQUEST_DIGEST, lease.requestDigest());
        assertEquals(PAYLOAD, lease.requestPayload());
        assertEquals(2, lease.attemptCount());
        assertEquals(EXPIRY, lease.leaseExpiresAt());
    }

    @Test
    void rejectsMissingUnsafeOrImpossibleFields() {
        assertThrows(
                NullPointerException.class,
                () -> lease(null, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, null, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, null, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, "unsafe owner",
                        PRINCIPAL_SCOPE_HASH, SUBMISSION_KEY_HASH, REQUEST_DIGEST,
                        PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, null,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, "A".repeat(64),
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        null, REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        "short", REQUEST_DIGEST, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, null, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, "g".repeat(64), PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, null, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 0, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> lease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PRINCIPAL_SCOPE_HASH,
                        SUBMISSION_KEY_HASH, REQUEST_DIGEST, PAYLOAD, 1, null)
        );
    }

    private static EtlJobLease validLease() {
        return lease(
                JOB_RECORD_ID,
                LEASE_CLAIM_ID,
                OWNER_ID,
                PRINCIPAL_SCOPE_HASH,
                SUBMISSION_KEY_HASH,
                REQUEST_DIGEST,
                PAYLOAD,
                2,
                EXPIRY
        );
    }

    private static EtlJobLease lease(
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
        return new EtlJobLease(
                jobRecordId,
                leaseClaimId,
                leaseOwnerId,
                principalScopeHash,
                submissionKeyHash,
                requestDigest,
                requestPayload,
                attemptCount,
                leaseExpiresAt
        );
    }
}
