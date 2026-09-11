package com.xtrmetl.etl.connector;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-process registry of known target connectors (supported + scaffold).
 * Not wired into {@code EtlService} write path yet — discovery/docs aid only.
 *
 * <p>Connector identifiers are catalog/config authority. Registration therefore fails closed for
 * null connectors, blank identifiers, and duplicate identifiers instead of allowing registration
 * order to replace the implementation that owns a configured connector id.</p>
 */
@Component
public class TargetConnectorRegistry {

    private final Map<String, TargetConnector> byId = new LinkedHashMap<>();

    public TargetConnectorRegistry() {
        register(new DatabricksTargetConnector());
        register(new SnowflakeTargetConnector());
    }

    /**
     * Creates a registry from an explicit connector list, primarily for standalone use and tests.
     *
     * @param connectors target connectors to register; a null list means no explicit connectors
     * @throws IllegalArgumentException when a connector is null, has a blank id, or duplicates
     *     an earlier id
     */
    public TargetConnectorRegistry(List<TargetConnector> connectors) {
        if (connectors != null) {
            connectors.forEach(this::register);
        }
        if (byId.isEmpty()) {
            register(new DatabricksTargetConnector());
            register(new SnowflakeTargetConnector());
        }
    }

    /**
     * Registers one target connector without allowing existing catalog identity to be replaced.
     *
     * @param connector target connector to register
     * @throws IllegalArgumentException when the connector is null, its id is blank, or its id is
     *     already registered
     */
    public final void register(TargetConnector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("Target connector must not be null");
        }
        String id = Objects.requireNonNullElse(connector.id(), "");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Target connector id must not be blank");
        }
        TargetConnector previous = byId.putIfAbsent(id, connector);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate target connector id: " + id);
        }
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
