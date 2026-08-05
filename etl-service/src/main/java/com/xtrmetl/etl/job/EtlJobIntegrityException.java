package com.xtrmetl.etl.job;

/**
 * Signals that persisted durable-job execution identity no longer matches retained or ledger data.
 *
 * <p>The exception exposes only one stable machine-readable failure code and a non-sensitive
 * message. It deliberately omits payloads, hashes, identifiers, SQL, timestamps, and stored
 * response bodies so accidental logging does not disclose customer or operational data.</p>
 */
public class EtlJobIntegrityException extends RuntimeException {

    /** Stable terminal failure code for payload or response-ledger integrity mismatches. */
    public static final String FAILURE_CODE = "etl_job_integrity_failure";

    /**
     * Creates a non-sensitive integrity failure signal.
     */
    public EtlJobIntegrityException() {
        super("Durable ETL job execution identity failed integrity validation");
    }

    /**
     * Returns the stable machine-readable terminal failure classification.
     *
     * @return {@value #FAILURE_CODE}
     */
    public String failureCode() {
        return FAILURE_CODE;
    }
}
