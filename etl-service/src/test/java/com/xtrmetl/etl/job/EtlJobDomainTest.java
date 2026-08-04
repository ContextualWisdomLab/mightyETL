package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines strict status, record, claim, and owner-safe view invariants for durable ETL jobs.
 */
class EtlJobDomainTest {

    private static final UUID JOB_ID = UUID.fromString("7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-04T10:30:00Z");

    @Test
    void mapsOnlyTheFourStableLowercaseStatuses() {
        for (EtlJobStatus status : EtlJobStatus.values()) {
            assertEquals(status, EtlJobStatus.fromDatabase(status.wireValue()));
            assertEquals(status.wireValue(), status.toString());
        }

        assertThrows(NullPointerException.class, () -> EtlJobStatus.fromDatabase(null));
        assertThrows(IllegalArgumentException.class, () -> EtlJobStatus.fromDatabase("cancelled"));
        assertThrows(IllegalArgumentException.class, () -> EtlJobStatus.fromDatabase("PENDING"));
    }

    @Test
    void createsAnOwnerSafeViewFromAnInternalStoredRecord() {
        EtlJobRecord record = new EtlJobRecord(
                JOB_ID,
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "[{\"id\":\"record_alpha\"}]",
                EtlJobStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                SUBMITTED_AT,
                null,
                null,
                SUBMITTED_AT
        );

        EtlJobView view = record.toView();

        assertEquals(JOB_ID, view.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, view.jobStatus());
        assertEquals("/api/etl/jobs/" + JOB_ID, view.statusUrl());
        assertEquals(0, view.attemptCount());
        assertEquals(SUBMITTED_AT, view.submittedAt());
        assertNull(view.startedAt());
        assertNull(view.completedAt());
        assertNull(view.responseBody());
        assertNull(view.failureCode());
    }

    @Test
    void rejectsUnsafeStoredRecordAndViewValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobView(
                        JOB_ID,
                        EtlJobStatus.PENDING,
                        "/api/etl/jobs/" + JOB_ID,
                        -1,
                        SUBMITTED_AT,
                        null,
                        null,
                        null,
                        null
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobRecord(
                        null,
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        "payload",
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        null,
                        null,
                        SUBMITTED_AT,
                        null,
                        null,
                        SUBMITTED_AT
                )
        );
    }

    @Test
    void requiresACompleteClaimLeaseAndPayload() {
        EtlJobClaim claim = new EtlJobClaim(
                JOB_ID,
                "a".repeat(64),
                "[{\"id\":\"record_alpha\"}]",
                1,
                "worker_alpha:lease_beta"
        );

        assertEquals(JOB_ID, claim.jobRecordId());
        assertEquals(1, claim.attemptCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobClaim(
                        JOB_ID,
                        "a".repeat(64),
                        "payload",
                        0,
                        "worker_alpha:lease_beta"
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobClaim(JOB_ID, "a".repeat(64), null, 1, "lease")
        );
    }
}
