package com.xtrmetl.cdc.spi;

import java.util.List;
import java.util.Map;

/**
 * SPI for CDC sink/targets (Kafka today; JDBC replica / warehouses later).
 */
public interface CdcTargetConnector extends AutoCloseable {

    String id();

    String displayName();

    /**
     * {@code true} when this is documentation/scaffold only.
     */
    boolean scaffoldOnly();

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
