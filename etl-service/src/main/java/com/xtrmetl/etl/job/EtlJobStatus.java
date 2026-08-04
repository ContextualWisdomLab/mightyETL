package com.xtrmetl.etl.job;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stable lifecycle states for one durable asynchronous ETL job.
 */
public enum EtlJobStatus {

    /** The validated job is available for a worker claim. */
    PENDING("pending"),

    /** One lease owner is currently attempting the job. */
    RUNNING("running"),

    /** Target effects and the durable response have committed. */
    SUCCEEDED("succeeded"),

    /** The job reached a terminal failure with a stable machine code. */
    FAILED("failed");

    private final String wireValue;

    EtlJobStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the lowercase value used by PostgreSQL and JSON responses.
     *
     * @return stable lowercase state
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Parses one strict lowercase database value.
     *
     * @param value database status value
     * @return matching stable status
     * @throws NullPointerException when {@code value} is null
     * @throws IllegalArgumentException when the value is not a supported state
     */
    public static EtlJobStatus fromDatabase(String value) {
        String requiredValue = Objects.requireNonNull(value, "value must not be null");
        return Arrays.stream(values())
                .filter(status -> status.wireValue.equals(requiredValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported ETL job status: " + requiredValue
                ));
    }

    /**
     * Returns the stable lowercase representation.
     *
     * @return lowercase state
     */
    @Override
    public String toString() {
        return wireValue;
    }
}
