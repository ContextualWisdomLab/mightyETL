package com.xtrmetl.cdc.spi;

import java.util.List;
import java.util.Map;

/**
 * Documents the live Kafka publish target. Actual sends remain in {@code CdcService}
 * (raw Debezium JSON) until the pipeline is switched to canonical records.
 */
public final class KafkaCdcTargetConnector implements CdcTargetConnector {

    public static final String ID = "kafka";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Apache Kafka (change events)";
    }

    @Override
    public boolean scaffoldOnly() {
        return false;
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
                "Live Kafka publish still uses raw Debezium envelopes via CdcService; "
                        + "canonical-record routing is not wired yet. See docs/cdc/any-to-any-cdc.md"
        );
    }
}
