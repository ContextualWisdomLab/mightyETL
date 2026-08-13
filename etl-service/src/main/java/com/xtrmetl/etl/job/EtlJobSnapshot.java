package com.xtrmetl.etl.job;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Operator-safe representation of one durable asynchronous ETL job.
 *
 * <p>The snapshot deliberately excludes the authenticated principal, submission key, request
 * payload, and internal hashes. Those values are either never persisted in plaintext or are not
 * part of the client-visible status resource.</p>
 *
 * @param jobRecordId opaque durable job identifier
 * @param jobStatus current stable lifecycle state
 * @param attemptCount number of worker claims recorded for this job
 * @param failureCode non-blank stable terminal failure code when {@code jobStatus} is
 *                    {@link EtlJobStatus#FAILED}; otherwise {@code null}
 * @param createdAt creation timestamp
 * @param updatedAt most recent state-change timestamp
 */
public record EtlJobSnapshot(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        int attemptCount,
        @Nullable String failureCode,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Validates the immutable status representation and its lifecycle-dependent failure metadata.
     *
     * @param jobRecordId opaque durable job identifier
     * @param jobStatus current stable lifecycle state
     * @param attemptCount non-negative number of worker claims
     * @param failureCode non-blank stable failure code exactly when the job has failed
     * @param createdAt creation timestamp
     * @param updatedAt most recent state-change timestamp
     */
    public EtlJobSnapshot {
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
}
