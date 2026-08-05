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
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final Instant EXPIRY = Instant.parse("2026-08-05T01:00:00Z");

    @Test
    void retainsEveryValidatedClaimField() {
        EtlJobLease lease = new EtlJobLease(
                JOB_RECORD_ID,
                LEASE_CLAIM_ID,
                OWNER_ID,
                PAYLOAD,
                2,
                EXPIRY
        );

        assertEquals(JOB_RECORD_ID, lease.jobRecordId());
        assertEquals(LEASE_CLAIM_ID, lease.leaseClaimId());
        assertEquals(OWNER_ID, lease.leaseOwnerId());
        assertEquals(PAYLOAD, lease.requestPayload());
        assertEquals(2, lease.attemptCount());
        assertEquals(EXPIRY, lease.leaseExpiresAt());
    }

    @Test
    void rejectsMissingUnsafeOrImpossibleFields() {
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobLease(null, LEASE_CLAIM_ID, OWNER_ID, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobLease(JOB_RECORD_ID, null, OWNER_ID, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobLease(JOB_RECORD_ID, LEASE_CLAIM_ID, null, PAYLOAD, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobLease(
                        JOB_RECORD_ID,
                        LEASE_CLAIM_ID,
                        "unsafe owner",
                        PAYLOAD,
                        1,
                        EXPIRY
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobLease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, null, 1, EXPIRY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobLease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PAYLOAD, 0, EXPIRY)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobLease(JOB_RECORD_ID, LEASE_CLAIM_ID, OWNER_ID, PAYLOAD, 1, null)
        );
    }
}
