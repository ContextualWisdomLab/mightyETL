package com.xtrmetl.cdc.spi;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of CDC source connector types discovered as Spring beans, plus a safe
 * fallback for unit tests without a Spring context.
 *
 * <p>Connector identifiers are configuration authority. Registration therefore fails closed for
 * null connectors, blank identifiers, and duplicate identifiers instead of allowing bean order to
 * replace the selected implementation.</p>
 */
@Component
public class CdcSourceRegistry {

    private final Map<String, CdcSourceConnector> byId = new LinkedHashMap<>();

    /**
     * Creates a registry from Spring-discovered source connectors.
     *
     * @param connectors ordered provider of source connector beans
     * @throws IllegalArgumentException when a discovered connector has an invalid or duplicate id
     */
    public CdcSourceRegistry(ObjectProvider<CdcSourceConnector> connectors) {
        connectors.orderedStream().forEach(this::register);
        if (byId.isEmpty()) {
            // Unit tests / non-Spring construction
            register(new PostgresDebeziumCdcSource());
        }
    }

    /**
     * Creates a registry from an explicit connector list, primarily for standalone use and tests.
     *
     * @param connectors source connectors to register; a null list means no explicit connectors
     * @throws IllegalArgumentException when a connector has an invalid or duplicate id
     */
    public CdcSourceRegistry(List<CdcSourceConnector> connectors) {
        if (connectors != null) {
            connectors.forEach(this::register);
        }
        if (byId.isEmpty()) {
            register(new PostgresDebeziumCdcSource());
        }
    }

    /**
     * Creates a registry containing the built-in PostgreSQL Debezium source connector.
     */
    public CdcSourceRegistry() {
        this(List.of());
    }

    /**
     * Registers one source connector without allowing existing configuration identity to be replaced.
     *
     * @param connector source connector to register
     * @throws IllegalArgumentException when the connector is null, its id is blank, or its id is
     *     already registered
     */
    public final void register(CdcSourceConnector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("CDC source connector must not be null");
        }
        String id = Objects.requireNonNullElse(connector.id(), "");
        if (id.isBlank()) {
            throw new IllegalArgumentException("CDC source connector id must not be blank");
        }
        CdcSourceConnector previous = byId.putIfAbsent(id, connector);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate CDC source connector id: " + id);
        }
    }

    /**
     * Finds a source connector by its exact configuration identifier.
     *
     * @param id exact connector identifier
     * @return the registered connector, or empty when the identifier is unknown
     */
    public Optional<CdcSourceConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns an immutable insertion-ordered snapshot of registered source connectors.
     *
     * @return immutable connector collection detached from registry mutation authority
     */
    public Collection<CdcSourceConnector> all() {
        return List.copyOf(byId.values());
    }
}
