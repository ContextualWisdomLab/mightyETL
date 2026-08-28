package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of CDC target connector types such as Kafka and JDBC replica targets.
 *
 * <p>Connector identifiers are configuration authority. Invalid registration is rejected so
 * registration order cannot silently replace a selected connector implementation.</p>
 */
@Component
public class CdcTargetRegistry {

    private final Map<String, CdcTargetConnector> byId = new LinkedHashMap<>();

    /** Creates a registry containing the built-in Kafka and JDBC replica target connectors. */
    public CdcTargetRegistry() {
        register(new KafkaCdcTargetConnector());
        register(new JdbcReplicaCdcTargetConnector());
    }

    /**
     * Registers one target connector without replacing an existing connector with the same id.
     *
     * @param connector target connector to register
     * @throws IllegalArgumentException when the connector is null, its id is blank, or its id is already registered
     */
    public final void register(CdcTargetConnector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("CDC target connector must not be null");
        }
        String id = Objects.requireNonNullElse(connector.id(), "");
        if (id.isBlank()) {
            throw new IllegalArgumentException("CDC target connector id must not be blank");
        }
        CdcTargetConnector previous = byId.putIfAbsent(id, connector);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate CDC target connector id: " + id);
        }
    }

    /**
     * Finds a target connector by its exact configuration identifier.
     *
     * @param id exact connector identifier
     * @return the registered connector, or empty when the identifier is unknown
     */
    public Optional<CdcTargetConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns an immutable insertion-ordered snapshot of registered target connectors.
     *
     * @return immutable connector collection detached from registry mutation authority
     */
    public Collection<CdcTargetConnector> all() {
        return List.copyOf(byId.values());
    }
}
