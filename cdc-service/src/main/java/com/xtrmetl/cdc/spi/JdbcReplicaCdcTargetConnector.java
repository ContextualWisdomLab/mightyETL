package com.xtrmetl.cdc.spi;

import java.util.List;
import java.util.Map;

/**
 * Documents the optional Postgres replica apply path ({@code processed_data} only today).
 */
public final class JdbcReplicaCdcTargetConnector implements CdcTargetConnector {

    public static final String ID = "jdbc-replica";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "PostgreSQL JDBC replica (processed_data)";
    }

    @Override
    public boolean scaffoldOnly() {
        return false;
    }

    /**
     * Reports that replica apply is live through the processed-data applier while canonical SPI writes remain unwired.
     *
     * @return immutable JDBC replica target execution metadata
     */
    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, DeliveryMode.PROCESSED_DATA_JDBC_REPLICA);
    }

    @Override
    public void validate(Map<String, String> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
    }

    @Override
    public void write(List<CanonicalChangeRecord> batch) {
        throw new UnsupportedOperationException(
                "Replica apply is owned by ProcessedDataReplicaApplier (table processed_data only); "
                        + "SPI write path not wired. See docs/cdc/ops-and-reliability.md"
        );
    }
}
