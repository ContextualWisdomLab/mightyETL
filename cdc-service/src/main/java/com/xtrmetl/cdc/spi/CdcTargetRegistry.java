package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of CDC target types (Kafka, JDBC replica, future warehouses).
 */
@Component
public class CdcTargetRegistry {

    private final Map<String, CdcTargetConnector> byId = new LinkedHashMap<>();

    public CdcTargetRegistry() {
        register(new KafkaCdcTargetConnector());
        register(new JdbcReplicaCdcTargetConnector());
    }

    public final void register(CdcTargetConnector connector) {
        byId.put(connector.id(), connector);
    }

    public Optional<CdcTargetConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<CdcTargetConnector> all() {
        return byId.values();
    }
}
