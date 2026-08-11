package com.xtrmetl.etl.connector;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-process registry of known target connectors (supported + scaffold).
 * Not wired into {@code EtlService} write path yet — discovery/docs aid only.
 */
@Component
public class TargetConnectorRegistry {

    private final Map<String, TargetConnector> byId = new LinkedHashMap<>();

    public TargetConnectorRegistry() {
        register(new DatabricksTargetConnector());
        register(new SnowflakeTargetConnector());
    }

    public final void register(TargetConnector connector) {
        byId.put(connector.id(), connector);
    }

    public Optional<TargetConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<TargetConnector> all() {
        return byId.values();
    }
}
