package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers immutable durable-job API and service model invariants.
 */
class EtlJobModelTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final Instant CREATED_AT = Instant.parse("2026-08-04T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-04T10:00:01Z");

    @Test
    void retainsValidSubmissionAndAcceptedResponseValues() {
        EtlJobSubmission submission = new EtlJobSubmission(
                JOB_RECORD_ID,
                EtlJobStatus.PENDING,
                false
        );
        EtlJobAcceptedResponse response = new EtlJobAcceptedResponse(
                JOB_RECORD_ID,
                EtlJobStatus.PENDING,
                "/api/etl/jobs/" + JOB_RECORD_ID
        );

        assertEquals(JOB_RECORD_ID, submission.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, submission.jobStatus());
        assertFalse(submission.replayed());
        assertEquals(JOB_RECORD_ID, response.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, response.jobStatus());
        assertEquals("/api/etl/jobs/" + JOB_RECORD_ID, response.statusUrl());
    }

    @Test
    void retainsValidSnapshotsAndMapsThemToStatusResponses() {
        EtlJobSnapshot snapshot = new EtlJobSnapshot(
                JOB_RECORD_ID,
                EtlJobStatus.PENDING,
                0,
                null,
                CREATED_AT,
                UPDATED_AT
        );
        EtlJobStatusResponse response = EtlJobStatusResponse.from(snapshot);

        assertEquals(JOB_RECORD_ID, response.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, response.jobStatus());
        assertEquals(0, response.attemptCount());
        assertNull(response.failureCode());
        assertEquals(CREATED_AT, response.createdAt());
        assertEquals(UPDATED_AT, response.updatedAt());
    }

    @Test
    void rejectsNullRequiredModelValues() {
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSubmission(null, EtlJobStatus.PENDING, false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSubmission(JOB_RECORD_ID, null, false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobAcceptedResponse(null, EtlJobStatus.PENDING, "status")
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobAcceptedResponse(JOB_RECORD_ID, null, "status")
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobAcceptedResponse(JOB_RECORD_ID, EtlJobStatus.PENDING, null)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(null, EtlJobStatus.PENDING, 0, null, CREATED_AT, UPDATED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(JOB_RECORD_ID, null, 0, null, CREATED_AT, UPDATED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(JOB_RECORD_ID, EtlJobStatus.PENDING, 0, null, null, UPDATED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(JOB_RECORD_ID, EtlJobStatus.PENDING, 0, null, CREATED_AT, null)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(null, EtlJobStatus.PENDING, 0, null, CREATED_AT, UPDATED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(JOB_RECORD_ID, null, 0, null, CREATED_AT, UPDATED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(JOB_RECORD_ID, EtlJobStatus.PENDING, 0, null, null, UPDATED_AT)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(JOB_RECORD_ID, EtlJobStatus.PENDING, 0, null, CREATED_AT, null)
        );
        assertThrows(NullPointerException.class, () -> EtlJobStatusResponse.from(null));
    }

    @Test
    void rejectsNegativeAttemptCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        -1,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        -1,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
    }

    @Test
    void rejectsFailureCodeOutsideFailedState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.SUCCEEDED,
                        1,
                        "target_write_failed",
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.RUNNING,
                        1,
                        "target_write_failed",
                        CREATED_AT,
                        UPDATED_AT
                )
        );
    }

    @Test
    void requiresNonBlankFailureCodeForFailedState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.FAILED,
                        1,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.FAILED,
                        1,
                        "   ",
                        CREATED_AT,
                        UPDATED_AT
                )
        );
    }
}
