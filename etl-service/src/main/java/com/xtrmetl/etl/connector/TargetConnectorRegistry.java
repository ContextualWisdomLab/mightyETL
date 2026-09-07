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

    private final Map<String, TargetConnector> targetConnectorsById = new LinkedHashMap<>();

    public TargetConnectorRegistry() {
        register(new DatabricksTargetConnector());
        register(new SnowflakeTargetConnector());
        register(new QlikSenseTargetConnector());
    }

    public final void register(TargetConnector targetConnector) {
        targetConnectorsById.put(targetConnector.targetId(), targetConnector);
    }

    public Optional<TargetConnector> find(String targetId) {
        return Optional.ofNullable(targetConnectorsById.get(targetId));
    }

    public Collection<TargetConnector> all() {
        return targetConnectorsById.values();
    }
}
