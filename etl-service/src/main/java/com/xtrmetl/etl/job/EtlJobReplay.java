package com.xtrmetl.etl.job;

import java.util.Objects;
import java.util.UUID;

/**
 * Reports one newly accepted or replayed immutable-lineage durable ETL job.
 *
 * <p>The source terminal resource and lineage remain internal persistence evidence. This result
 * exposes only the new opaque job identifier, its required initial pending state, and whether the
 * same principal-scoped replay request had already created it.</p>
 *
 * @param jobRecordId newly created durable job identifier
 * @param jobStatus required initial {@link EtlJobStatus#PENDING} state
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
        EtlJobStatus requiredStatus = Objects.requireNonNull(
                jobStatus,
                "jobStatus must not be null"
        );
        if (requiredStatus != EtlJobStatus.PENDING) {
            throw new IllegalArgumentException("replay jobStatus must be PENDING");
        }
    }
}
