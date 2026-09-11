package com.xtrmetl.etl.connector;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Returns an immutable insertion-ordered snapshot of registered target connectors.
     *
     * <p>The returned collection is detached from registry mutation authority: attempts to
     * {@code add}, {@code remove}, {@code clear}, or remove through its iterator fail, and a
     * previously returned snapshot does not change when a later connector is registered. Callers
     * cannot alter connector discovery or execution authority through this enumeration.</p>
     *
     * @return immutable snapshot preserving registration order and connector identity
     */
    public Collection<TargetConnector> all() {
        return List.copyOf(byId.values());
    }
}
