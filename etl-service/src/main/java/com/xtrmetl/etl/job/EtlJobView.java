package com.xtrmetl.etl.job;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Owner-safe representation returned by asynchronous ETL job endpoints.
 *
 * <p>The representation intentionally omits principal hashes, submission hashes, request digests,
 * request payloads, and lease identifiers.</p>
 *
 * @param jobRecordId stable opaque job identifier
 * @param jobStatus current lifecycle status
 * @param statusUrl owner-scoped relative status-monitor URI
 * @param attemptCount number of committed worker claims
 * @param submittedAt database submission time
 * @param startedAt first claim time, when available
 * @param completedAt terminal completion time, when available
 * @param responseBody successful ETL response, when available
 * @param failureCode stable terminal failure code, when available
 */
public record EtlJobView(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        String statusUrl,
        int attemptCount,
        Instant submittedAt,
        @Nullable Instant startedAt,
        @Nullable Instant completedAt,
        @Nullable String responseBody,
        @Nullable String failureCode
) {

    /**
     * Validates immutable public representation invariants.
     */
    public EtlJobView {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
        Objects.requireNonNull(statusUrl, "statusUrl must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
