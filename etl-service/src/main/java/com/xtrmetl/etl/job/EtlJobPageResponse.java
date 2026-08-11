package com.xtrmetl.etl.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Client-visible owner-scoped page of durable ETL job status resources.
 *
 * <p>Each item is converted through {@link EtlJobStatusResponse}, so retained payloads, raw
 * principals, idempotency keys, hashes, SQL, and exception text cannot enter the page response.
 * The next cursor is omitted from JSON when the page is terminal.</p>
 *
 * @param jobs immutable client-safe job status representations
 * @param nextCursor opaque following-page cursor, omitted when no page follows
 */
public record EtlJobPageResponse(
        List<EtlJobStatusResponse> jobs,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String nextCursor
) {

    /**
     * Validates and defensively copies the immutable response page.
     *
     * @param jobs non-null client-safe statuses without null elements
     * @param nextCursor opaque following-page cursor, or {@code null}
     */
    public EtlJobPageResponse {
        jobs = List.copyOf(Objects.requireNonNull(jobs, "jobs must not be null"));
    }

    /**
     * Converts an internal owner-scoped page into the public wire representation.
     *
     * @param page immutable internal job page
     * @return client-safe page response
     */
    public static EtlJobPageResponse from(EtlJobPage page) {
        EtlJobPage requiredPage = Objects.requireNonNull(page, "page must not be null");
        List<EtlJobStatusResponse> responses = requiredPage.jobs().stream()
                .map(EtlJobStatusResponse::from)
                .toList();
        return new EtlJobPageResponse(responses, requiredPage.nextCursor());
    }
}
