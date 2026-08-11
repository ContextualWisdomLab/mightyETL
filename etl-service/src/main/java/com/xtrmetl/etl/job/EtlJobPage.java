package com.xtrmetl.etl.job;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable owner-scoped page of operator-safe durable ETL job snapshots.
 *
 * <p>The list is defensively copied so callers cannot mutate a page after the service has derived
 * its next-cursor boundary. The optional cursor is opaque to clients and identifies the last item
 * returned by this page; it is absent when the current page is terminal.</p>
 *
 * @param jobs immutable operator-safe job snapshots in deterministic newest-first order
 * @param nextCursor opaque cursor for the following page, or {@code null} when no page follows
 */
public record EtlJobPage(
        List<EtlJobSnapshot> jobs,
        @Nullable String nextCursor
) {

    /**
     * Validates and defensively copies the immutable page.
     *
     * @param jobs non-null snapshots without null elements
     * @param nextCursor opaque following-page cursor, or {@code null}
     */
    public EtlJobPage {
        jobs = List.copyOf(Objects.requireNonNull(jobs, "jobs must not be null"));
    }
}
