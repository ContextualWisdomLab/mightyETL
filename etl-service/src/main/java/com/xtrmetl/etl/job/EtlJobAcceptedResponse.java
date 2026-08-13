package com.xtrmetl.etl.job;

import java.util.Objects;
import java.util.UUID;

/**
 * RFC 9110 {@code 202 Accepted} representation for a durable ETL job submission.
 *
 * @param jobRecordId opaque durable job identifier
 * @param jobStatus current stable job status
 * @param statusUrl origin-relative status-monitor resource URL beginning with exactly one slash
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
     * @param statusUrl origin-relative status-monitor resource URL beginning with exactly one slash
     * @throws IllegalArgumentException when {@code statusUrl} is blank or is not origin-relative
     */
    public EtlJobAcceptedResponse {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        Objects.requireNonNull(statusUrl, "statusUrl must not be null");
        if (statusUrl.isBlank()) {
            throw new IllegalArgumentException("statusUrl must not be blank");
        }
        if (!statusUrl.startsWith("/") || statusUrl.startsWith("//")) {
            throw new IllegalArgumentException("statusUrl must be origin-relative");
        }
    }
}
