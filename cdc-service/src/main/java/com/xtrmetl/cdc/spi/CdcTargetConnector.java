package com.xtrmetl.cdc.spi;

import java.util.List;
import java.util.Map;

/**
 * SPI for CDC sink/targets (Kafka today; JDBC replica / warehouses later).
 */
public interface CdcTargetConnector extends AutoCloseable {

    /**
     * Describes how the currently shipped product path delivers events to a target.
     */
    enum DeliveryMode {
        /** Raw Debezium envelopes are published by {@code CdcService} to Kafka. */
        RAW_DEBEZIUM_KAFKA,
        /** Processed-data rows are applied by the dedicated JDBC replica pipeline. */
        PROCESSED_DATA_JDBC_REPLICA,
        /** Canonical records are delivered directly through this SPI's {@link #write(List)} method. */
        CANONICAL_RECORD_SPI
    }

    /**
     * Truthful execution metadata for a target connector.
     *
     * @param productPathLive whether mightyETL currently has a live product path for the target
     * @param canonicalWriteSupported whether {@link #write(List)} is wired for canonical records
     * @param deliveryMode the execution boundary used by the live product path
     */
    record Capabilities(
            boolean productPathLive,
            boolean canonicalWriteSupported,
            DeliveryMode deliveryMode
    ) {
    }

    String id();

    String displayName();

    /**
     * {@code true} when this is documentation/scaffold only.
     */
    boolean scaffoldOnly();

    /**
     * Returns execution metadata without conflating a live legacy/product path with SPI write support.
     *
     * @return immutable target capability metadata
     */
    Capabilities capabilities();

    void validate(Map<String, String> config);

    /**
     * Apply a batch of canonical change records.
     */
    void write(List<CanonicalChangeRecord> batch);

    @Override
    default void close() {
        // no-op
    }
}
