package com.xtrmetl.etl.job;

import java.util.Objects;
import java.util.UUID;

/**
 * Describes the result of creating or replaying one durable ETL job submission.
 *
 * @param jobRecordId opaque durable job identifier
 * @param jobStatus current stable job status
 * @param replayed {@code true} when an existing principal-scoped submission was returned
 */
public record EtlJobSubmission(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        boolean replayed
) {

    /**
     * Validates the immutable job submission result.
     *
     * @param jobRecordId opaque durable job identifier
     * @param jobStatus current stable job status
     * @param replayed whether this result represents a prior submission
     */
    public EtlJobSubmission {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
    }
}
