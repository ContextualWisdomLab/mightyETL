package com.xtrmetl.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource limits for JSON batch processing in {@code etl-service}.
 *
 * <p>The preferred operator prefix is {@code mightyetl.etl.*}; the existing environment
 * post-processor mirrors it onto the compatibility prefix {@code xtrmetl.etl.*} used for
 * binding.</p>
 */
@ConfigurationProperties(prefix = "xtrmetl.etl")
public class EtlProcessingProperties {

    /** Maximum accepted JSON records in one request. */
    private int maxBatchRecords = 1000;

    /** Number of dedicated transformation workers. */
    private int maxConcurrency = Math.max(
            1,
            Math.min(8, Runtime.getRuntime().availableProcessors())
    );

    /** Number of queued transformation tasks before caller-runs backpressure applies. */
    private int queueCapacity = 1024;

    public int getMaxBatchRecords() {
        return maxBatchRecords;
    }

    public void setMaxBatchRecords(int maxBatchRecords) {
        this.maxBatchRecords = maxBatchRecords;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * Validates operator-supplied limits before any worker threads are created.
     *
     * @throws IllegalArgumentException when a value is outside the supported safety range
     */
    public void validate() {
        requireRange("max-batch-records", maxBatchRecords, 1, 100_000);
        requireRange("max-concurrency", maxConcurrency, 1, 64);
        requireRange("queue-capacity", queueCapacity, 1, 100_000);
    }

    private static void requireRange(String property, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    "xtrmetl.etl." + property + " must be between "
                            + minimum + " and " + maximum + "; was " + value
            );
        }
    }
}
