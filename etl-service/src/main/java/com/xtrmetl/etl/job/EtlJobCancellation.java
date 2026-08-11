package com.xtrmetl.etl.job;

import java.util.Objects;

/**
 * Reports one committed or replayed owner-scoped durable job cancellation.
 *
 * <p>The result contains only the existing operator-safe status snapshot and a replay flag. It
 * never exposes the authenticated principal, raw cancellation key, cancellation-key hash, lease
 * identity, request payload, SQL, or internal exception text.</p>
 *
 * @param snapshot cancelled owner-authorized job representation
 * @param replayed {@code true} when the same normalized cancellation key had already committed
 */
public record EtlJobCancellation(EtlJobSnapshot snapshot, boolean replayed) {

    /**
     * Validates the immutable cancellation result.
     *
     * @param snapshot cancelled owner-authorized job representation
     * @param replayed whether this response proves an earlier identical cancellation
     */
    public EtlJobCancellation {
        EtlJobSnapshot requiredSnapshot = Objects.requireNonNull(
                snapshot,
                "snapshot must not be null"
        );
        if (requiredSnapshot.jobStatus() != EtlJobStatus.CANCELLED) {
            throw new IllegalArgumentException("snapshot must be in CANCELLED state");
        }
    }
}
