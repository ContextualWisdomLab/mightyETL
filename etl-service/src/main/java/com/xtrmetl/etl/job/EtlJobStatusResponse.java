package com.xtrmetl.etl.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-visible status representation for one owner-scoped durable ETL job.
 *
 * @param jobRecordId opaque durable job identifier
 * @param jobStatus current stable lifecycle state
 * @param attemptCount number of worker claims recorded for this job
 * @param failureCode stable terminal failure code, omitted before failure
 * @param createdAt creation timestamp
 * @param updatedAt most recent state-change timestamp
 */
public record EtlJobStatusResponse(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        int attemptCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String failureCode,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Validates the immutable status response.
     *
     * @param jobRecordId opaque durable job identifier
     * @param jobStatus current stable lifecycle state
     * @param attemptCount non-negative worker claim count
     * @param failureCode stable terminal failure code, or {@code null}
     * @param createdAt creation timestamp
     * @param updatedAt most recent state-change timestamp
     */
    public EtlJobStatusResponse {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }

    /**
     * Creates the client response from an internal operator-safe job snapshot.
     *
     * @param snapshot owner-scoped job snapshot
     * @return API status representation
     */
    public static EtlJobStatusResponse from(EtlJobSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return new EtlJobStatusResponse(
                snapshot.jobRecordId(),
                snapshot.jobStatus(),
                snapshot.attemptCount(),
                snapshot.failureCode(),
                snapshot.createdAt(),
                snapshot.updatedAt()
        );
    }
}
