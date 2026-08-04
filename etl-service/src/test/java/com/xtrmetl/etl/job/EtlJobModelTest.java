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
        EtlJobSnapshot pending = new EtlJobSnapshot(
                JOB_RECORD_ID,
                EtlJobStatus.PENDING,
                0,
                null,
                null,
                CREATED_AT,
                UPDATED_AT
        );
        EtlJobSnapshot succeeded = new EtlJobSnapshot(
                JOB_RECORD_ID,
                EtlJobStatus.SUCCEEDED,
                1,
                null,
                2,
                CREATED_AT,
                UPDATED_AT
        );
        EtlJobStatusResponse pendingResponse = EtlJobStatusResponse.from(pending);
        EtlJobStatusResponse successResponse = EtlJobStatusResponse.from(succeeded);

        assertEquals(JOB_RECORD_ID, pendingResponse.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, pendingResponse.jobStatus());
        assertEquals(0, pendingResponse.attemptCount());
        assertNull(pendingResponse.failureCode());
        assertNull(pendingResponse.processedRecordCount());
        assertEquals(CREATED_AT, pendingResponse.createdAt());
        assertEquals(UPDATED_AT, pendingResponse.updatedAt());
        assertEquals(2, successResponse.processedRecordCount());
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
                () -> new EtlJobSnapshot(
                        null,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        null,
                        0,
                        null,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        null,
                        UPDATED_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        CREATED_AT,
                        null
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(
                        null,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        null,
                        0,
                        null,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        null,
                        UPDATED_AT
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        null,
                        CREATED_AT,
                        null
                )
        );
        assertThrows(NullPointerException.class, () -> EtlJobStatusResponse.from(null));
    }

    @Test
    void rejectsNegativeAttemptAndProcessedRecordCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        -1,
                        null,
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
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.SUCCEEDED,
                        1,
                        null,
                        -1,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobStatusResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.SUCCEEDED,
                        1,
                        null,
                        -1,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
    }
}
