package com.xtrmetl.etl.job;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-visible status representation for one owner-scoped durable ETL job.
 *
 * <p>Timestamps are explicitly serialized as ISO-8601 strings. The wire contract therefore does
 * not depend on an application-wide Jackson timestamp setting or on the HTTP adapter used by an
 * embedded integration.</p>
 *
 * @param jobRecordId opaque durable job identifier
 * @param jobStatus current stable lifecycle state
 * @param attemptCount number of worker claims recorded for this job
 * @param failureCode non-blank stable terminal failure code when {@code jobStatus} is
 *                    {@link EtlJobStatus#FAILED}; otherwise omitted as {@code null}
 * @param createdAt creation timestamp serialized as an ISO-8601 string
 * @param updatedAt most recent state-change timestamp serialized as an ISO-8601 string
 */
public record EtlJobStatusResponse(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        int attemptCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String failureCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {

    /**
     * Validates the immutable status response and its lifecycle-dependent failure metadata.
     *
     * @param jobRecordId opaque durable job identifier
     * @param jobStatus current stable lifecycle state
     * @param attemptCount non-negative worker claim count
     * @param failureCode non-blank stable failure code exactly when the job has failed
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
        if (jobStatus == EtlJobStatus.FAILED) {
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException(
                        "failureCode must be non-blank when jobStatus is FAILED"
                );
            }
        } else if (failureCode != null) {
            throw new IllegalArgumentException(
                    "failureCode must be null unless jobStatus is FAILED"
            );
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
