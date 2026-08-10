package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of CDC target types (Kafka, JDBC replica, future warehouses).
 *
 * <p>Connector IDs are unique authority selectors. Duplicate or blank identities fail closed
 * rather than silently replacing the implementation selected by operator configuration.</p>
 */
@Component
public class CdcTargetRegistry {

    private final Map<String, CdcTargetConnector> byId = new LinkedHashMap<>();

    /**
     * Creates the target registry with the built-in Kafka and JDBC-replica descriptors.
     */
    public CdcTargetRegistry() {
        register(new KafkaCdcTargetConnector());
        register(new JdbcReplicaCdcTargetConnector());
    }

    /**
     * Registers one target connector under a unique non-blank ID.
     *
     * @param connector connector to register
     * @throws IllegalArgumentException if the connector is null or its ID is blank or already registered
     */
    public final void register(CdcTargetConnector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("CDC target connector must not be null");
        }
        String id = connector.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CDC target connector id must not be blank");
        }
        if (byId.putIfAbsent(id, connector) != null) {
            throw new IllegalArgumentException("Duplicate CDC target connector id: " + id);
        }
    }

    /**
     * Finds a registered target connector by its exact ID.
     *
     * @param id connector ID
     * @return the registered connector, or empty when the ID is unknown
     */
    public Optional<CdcTargetConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns all registered target connectors in deterministic registration order.
     *
     * @return registered target connectors
     */
    public Collection<CdcTargetConnector> all() {
        return byId.values();
    }
}
