package com.xtrmetl.etl.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-safety limits for one {@link EtlService#processData(String)} request.
 *
 * <p>The limits are evaluated before any database write. Payload size is measured as UTF-8 bytes,
 * and record count is measured from the parsed top-level JSON array.</p>
 */
@ConfigurationProperties(prefix = "xtrmetl.etl")
public class EtlBatchProperties {

    /** Default maximum request payload: one mebibyte. */
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 1_048_576;

    /** Default maximum number of records in one request. */
    public static final int DEFAULT_MAX_BATCH_RECORDS = 1_000;

    private int maxPayloadBytes = DEFAULT_MAX_PAYLOAD_BYTES;
    private int maxBatchRecords = DEFAULT_MAX_BATCH_RECORDS;

    /**
     * Returns the maximum accepted UTF-8 request size.
     *
     * @return positive byte limit
     */
    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * Sets the maximum accepted UTF-8 request size.
     *
     * @param maxPayloadBytes positive byte limit
     * @throws IllegalArgumentException when the value is less than one
     */
    public void setMaxPayloadBytes(int maxPayloadBytes) {
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("xtrmetl.etl.max-payload-bytes must be at least 1");
        }
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /**
     * Returns the maximum number of records accepted in one request.
     *
     * @return positive record limit
     */
    public int getMaxBatchRecords() {
        return maxBatchRecords;
    }

    /**
     * Sets the maximum number of records accepted in one request.
     *
     * @param maxBatchRecords positive record limit
     * @throws IllegalArgumentException when the value is less than one
     */
    public void setMaxBatchRecords(int maxBatchRecords) {
        if (maxBatchRecords < 1) {
            throw new IllegalArgumentException("xtrmetl.etl.max-batch-records must be at least 1");
        }
        this.maxBatchRecords = maxBatchRecords;
    }
}
