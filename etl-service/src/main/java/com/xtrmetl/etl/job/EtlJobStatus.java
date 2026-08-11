package com.xtrmetl.etl.job;

/**
 * Stable lifecycle states exposed by the asynchronous ETL job resource.
 *
 * <p>Pending and running jobs retain a bounded request payload. Succeeded, failed, and cancelled
 * jobs are terminal and clear the payload. Exact database predicates, rather than scheduler or HTTP
 * request timing, determine which terminal outcome wins.</p>
 */
public enum EtlJobStatus {

    /** The durable request has been accepted and awaits worker execution. */
    PENDING,

    /** A lease-fenced worker currently owns execution of the durable request. */
    RUNNING,

    /** The target effects committed successfully and the retained payload was cleared. */
    SUCCEEDED,

    /** The job reached a terminal failure and the retained payload was cleared. */
    FAILED,

    /** The authenticated owner cancelled the job and invalidated any active lease. */
    CANCELLED
}
