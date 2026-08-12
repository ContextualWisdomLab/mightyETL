package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of CDC target types (Kafka, JDBC replica, future warehouses).
 *
 * <p>Connector identifiers are registration authority. Null connectors, blank
 * identifiers, and duplicate identifiers are rejected before the registry is
 * mutated so one implementation cannot silently replace another.</p>
 */
@Component
public class CdcTargetRegistry {

    private final Map<String, CdcTargetConnector> byId = new LinkedHashMap<>();

    /**
     * Builds a target registry with the built-in Kafka and JDBC replica targets.
     */
    public CdcTargetRegistry() {
        register(new KafkaCdcTargetConnector());
        register(new JdbcReplicaCdcTargetConnector());
    }

    /**
     * Registers a CDC target connector without permitting ambiguous authority.
     *
     * @param connector connector to register
     * @throws IllegalArgumentException when the connector is null, its identifier
     *                                  is blank, or its identifier is already registered
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
     * Finds a registered target connector by its exact identifier.
     *
     * @param id connector identifier
     * @return the connector when registered, otherwise empty
     */
    public Optional<CdcTargetConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns an unmodifiable live view of all registered target connectors in registration order.
     *
     * @return unmodifiable registered target connectors
     */
    public Collection<CdcTargetConnector> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
