package com.xtrmetl.etl.job;

/**
 * Stable lifecycle states exposed by the asynchronous ETL job resource.
 *
 * <p>This intake slice creates jobs only in {@link #PENDING}. The remaining values reserve the
 * compatibility-safe state names required by the following worker and lease-fencing slice, so a
 * deployed status reader can deserialize later transitions without a schema or API vocabulary
 * change.</p>
 */
public enum EtlJobStatus {

    /** The durable request has been accepted and awaits worker execution. */
    PENDING,

    /** A lease-fenced worker currently owns execution of the durable request. */
    RUNNING,

    /** The target effects committed successfully and the retained payload was cleared. */
    SUCCEEDED,

    /** The job reached a terminal failure and the retained payload was cleared. */
    FAILED
}
