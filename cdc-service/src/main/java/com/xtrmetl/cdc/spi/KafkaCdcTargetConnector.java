package com.xtrmetl.cdc.spi;

import java.util.List;
import java.util.Map;

/**
 * Documents the live Kafka publish target. Actual sends remain in {@code CdcService}
 * (raw Debezium JSON) until the pipeline is switched to canonical records.
 */
public final class KafkaCdcTargetConnector implements CdcTargetConnector {

    public static final String TARGET_ID = "kafka";

    /**
     * @deprecated compatibility alias; organization-owned callers use {@link #TARGET_ID}
     */
    @Deprecated(forRemoval = false)
    public static final String ID = TARGET_ID;

    @Override
    public String targetId() {
        return TARGET_ID;
    }

    /**
     * @deprecated compatibility alias; organization-owned callers use {@link #targetId()}
     */
    @Override
    @Deprecated(forRemoval = false)
    public String id() {
        return targetId();
    }

    @Override
    public String displayName() {
        return "Apache Kafka (change events)";
    }

    @Override
    public boolean scaffoldOnly() {
        return false;
    }

    /**
     * Reports that Kafka is live through the raw Debezium path while canonical SPI writes remain unwired.
     *
     * @return immutable Kafka target execution metadata
     */
    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, DeliveryMode.RAW_DEBEZIUM_KAFKA);
    }

    @Override
    public void validate(Map<String, String> targetConfig) {
        if (targetConfig == null) {
            throw new IllegalArgumentException("config must not be null");
        }
    }

    @Override
    public void write(List<CanonicalChangeRecord> changeBatch) {
        throw new UnsupportedOperationException(
                "Live Kafka publish still uses raw Debezium envelopes via CdcService; "
                        + "canonical-record routing is not wired yet. See docs/cdc/any-to-any-cdc.md"
        );
    }
}
