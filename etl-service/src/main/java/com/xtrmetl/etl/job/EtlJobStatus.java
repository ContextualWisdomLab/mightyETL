package com.xtrmetl.etl.job;

/**
 * Stable lifecycle states exposed by the asynchronous ETL job resource.
 *
 * <p>This intake slice creates jobs in {@link #PENDING}. Later worker slices may add transitions
 * while preserving the serialized names already exposed to API clients.</p>
 */
public enum EtlJobStatus {

    /** The durable request has been accepted and awaits worker execution. */
    PENDING
}
