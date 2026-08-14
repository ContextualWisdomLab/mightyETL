package com.xtrmetl.etl.job;

/**
 * Signals that a worker no longer owns the exact live lease required for a state transition.
 *
 * <p>The exception intentionally carries no job, claim, owner, payload, SQL, or timestamp values so
 * accidental logging cannot disclose operational identifiers or retained customer data.</p>
 */
public class StaleEtlJobLeaseException extends RuntimeException {

    /**
     * Creates the stable non-sensitive stale-lease signal.
     */
    public StaleEtlJobLeaseException() {
        super("The durable ETL job lease is stale or no longer owned");
    }
}
