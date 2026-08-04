package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines immutable lease-claim invariants used between worker components.
 */
class EtlJobClaimTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final UUID LEASE_TOKEN = UUID.fromString(
            "72ab52a5-b060-4a20-af99-e701722bb221"
    );
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final Instant CLAIMED_AT = Instant.parse("2026-08-04T12:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-08-04T12:05:00Z");

    @Test
    void retainsOneValidLeaseClaim() {
        EtlJobClaim claim = new EtlJobClaim(
                JOB_RECORD_ID,
                LEASE_TOKEN,
                PAYLOAD,
                1,
                CLAIMED_AT,
                LEASE_EXPIRES_AT
        );

        assertEquals(JOB_RECORD_ID, claim.jobRecordId());
        assertEquals(LEASE_TOKEN, claim.leaseToken());
        assertEquals(PAYLOAD, claim.requestPayload());
        assertEquals(1, claim.attemptCount());
        assertEquals(CLAIMED_AT, claim.claimedAt());
        assertEquals(LEASE_EXPIRES_AT, claim.leaseExpiresAt());
    }

    @Test
    void rejectsMissingClaimValues() {
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobClaim(
                        null,
                        LEASE_TOKEN,
                        PAYLOAD,
                        1,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        null,
                        PAYLOAD,
                        1,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        null,
                        1,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        PAYLOAD,
                        1,
                        null,
                        LEASE_EXPIRES_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        PAYLOAD,
                        1,
                        CLAIMED_AT,
                        null
                )
        );
    }

    @Test
    void rejectsBlankPayloadNonPositiveAttemptAndNonFutureExpiry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        " ",
                        1,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        PAYLOAD,
                        0,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        PAYLOAD,
                        1,
                        CLAIMED_AT,
                        CLAIMED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobClaim(
                        JOB_RECORD_ID,
                        LEASE_TOKEN,
                        PAYLOAD,
                        1,
                        CLAIMED_AT,
                        CLAIMED_AT.minusSeconds(1)
                )
        );
    }
}
