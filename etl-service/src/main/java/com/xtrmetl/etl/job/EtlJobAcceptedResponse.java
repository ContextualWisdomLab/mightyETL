package com.xtrmetl.etl.job;

import java.util.Objects;
import java.util.UUID;

/**
 * RFC 9110 {@code 202 Accepted} representation for a durable ETL job submission.
 *
 * @param jobRecordId opaque durable job identifier
 * @param jobStatus current stable job status
 * @param statusUrl relative status-monitor resource URL
 */
public record EtlJobAcceptedResponse(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        String statusUrl
) {

    /**
     * Validates the immutable accepted representation.
     *
     * @param jobRecordId opaque durable job identifier
     * @param jobStatus current stable job status
     * @param statusUrl relative status-monitor resource URL
     */
    public EtlJobAcceptedResponse {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        Objects.requireNonNull(statusUrl, "statusUrl must not be null");
    }
}
