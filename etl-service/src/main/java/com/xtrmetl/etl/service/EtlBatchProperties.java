package com.xtrmetl.etl.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-safety limits for one {@link EtlService#processData(String)} request.
 *
 * <p>The limits are evaluated before any database write. Payload size is measured as UTF-8 bytes,
 * and record count is measured from the parsed top-level JSON array. Upper bounds prevent an
 * accidental configuration change from effectively disabling admission control.</p>
 */
@ConfigurationProperties(prefix = "xtrmetl.etl")
public class EtlBatchProperties {

    /** Default maximum request payload: one mebibyte. */
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 1_048_576;

    /** Default maximum number of records in one request. */
    public static final int DEFAULT_MAX_BATCH_RECORDS = 1_000;

    /** Hard ceiling for one in-memory request payload: 64 mebibytes. */
    public static final int MAX_MAX_PAYLOAD_BYTES = 67_108_864;

    /** Hard ceiling for records in one synchronous transaction. */
    public static final int MAX_MAX_BATCH_RECORDS = 100_000;

    private int maxPayloadBytes = DEFAULT_MAX_PAYLOAD_BYTES;
    private int maxBatchRecords = DEFAULT_MAX_BATCH_RECORDS;

    /**
     * Returns the maximum accepted UTF-8 request size.
     *
     * @return byte limit between one and {@link #MAX_MAX_PAYLOAD_BYTES}
     */
    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * Sets the maximum accepted UTF-8 request size.
     *
     * @param maxPayloadBytes byte limit between one and {@link #MAX_MAX_PAYLOAD_BYTES}
     * @throws IllegalArgumentException when the value falls outside the supported range
     */
    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = requireRange(
                "max-payload-bytes",
                maxPayloadBytes,
                MAX_MAX_PAYLOAD_BYTES
        );
    }

    /**
     * Returns the maximum number of records accepted in one request.
     *
     * @return record limit between one and {@link #MAX_MAX_BATCH_RECORDS}
     */
    public int getMaxBatchRecords() {
        return maxBatchRecords;
    }

    /**
     * Sets the maximum number of records accepted in one request.
     *
     * @param maxBatchRecords record limit between one and {@link #MAX_MAX_BATCH_RECORDS}
     * @throws IllegalArgumentException when the value falls outside the supported range
     */
    public void setMaxBatchRecords(int maxBatchRecords) {
        this.maxBatchRecords = requireRange(
                "max-batch-records",
                maxBatchRecords,
                MAX_MAX_BATCH_RECORDS
        );
    }

    private static int requireRange(String property, int value, int maximum) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(
                    "xtrmetl.etl." + property + " must be between 1 and "
                            + maximum + "; was " + value
            );
        }
        return value;
    }
}
