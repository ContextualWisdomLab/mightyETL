package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Closes branch-coverage gaps in immutable durable-job model validation that are part of the
 * non-vacuous JaCoCo production slice.
 *
 * <p>These cases exercise each distinct short-circuit path in origin-relative status URL and
 * lifecycle-dependent failure-code validation. They are intentionally model-boundary tests: no
 * database, transaction, network, or controller fixture is needed to prove the record invariants.</p>
 */
class EtlJobModelCoverageBoundaryTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final Instant CREATED_AT = Instant.parse("2026-08-04T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-04T10:00:01Z");

    @Test
    void rejectsEveryUnsafeAcceptedResponseStatusUrlForm() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobAcceptedResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        "   "
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobAcceptedResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        "api/etl/jobs/" + JOB_RECORD_ID
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobAcceptedResponse(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        "//attacker.example/api/etl/jobs/" + JOB_RECORD_ID
                )
        );
    }

    @Test
    void coversBothFailedStateFailureCodeShortCircuitForms() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlJobSnapshot(
                        JOB_RECORD_ID,
                        EtlJobStatus.FAILED,
                        1,
                        "   ",
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
                        null,
                        CREATED_AT,
                        UPDATED_AT
                )
        );
    }
}
