package com.xtrmetl.etl.job;

import java.util.Objects;
import java.util.UUID;

/**
 * Reports one newly accepted or replayed immutable-lineage durable ETL job.
 *
 * <p>The source terminal resource and lineage remain internal persistence evidence. This result
 * exposes only the new opaque job identifier, its current stable lifecycle state, and whether the
 * same principal-scoped replay request had already created it. A first creation is pending; a later
 * idempotent retry may correctly report that the same created job has since progressed.</p>
 *
 * @param jobRecordId replay-created durable job identifier
 * @param jobStatus current stable lifecycle state of that created job
 * @param replayed {@code true} when this response reuses an already-created replay job
 */
public record EtlJobReplay(
        UUID jobRecordId,
        EtlJobStatus jobStatus,
        boolean replayed
) {

    /** Validates the immutable replay result. */
    public EtlJobReplay {
        Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        Objects.requireNonNull(jobStatus, "jobStatus must not be null");
    }
}
