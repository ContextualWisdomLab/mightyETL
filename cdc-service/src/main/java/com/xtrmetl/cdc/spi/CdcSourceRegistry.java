package com.xtrmetl.cdc.spi;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of CDC source connector types discovered as Spring beans, plus a safe
 * fallback for unit tests without a Spring context.
 *
 * <p>Connector IDs are unique authority selectors. Duplicate or blank identities fail closed
 * rather than letting Spring discovery order silently replace an earlier implementation.</p>
 */
@Component
public class CdcSourceRegistry {

    private final Map<String, CdcSourceConnector> byId = new LinkedHashMap<>();

    /**
     * Creates a source registry from Spring-discovered connectors in their configured order.
     *
     * @param connectors Spring provider for source connectors
     * @throws IllegalArgumentException if a discovered connector has a blank or duplicate ID
     */
    public CdcSourceRegistry(ObjectProvider<CdcSourceConnector> connectors) {
        connectors.orderedStream().forEach(this::register);
        if (byId.isEmpty()) {
            // Unit tests / non-Spring construction
            register(new PostgresDebeziumCdcSource());
        }
    }

    /**
     * Creates a source registry from an explicit connector list, primarily for tests and embedding.
     *
     * @param connectors connectors to register; a null list behaves like an empty list
     * @throws IllegalArgumentException if a connector has a blank or duplicate ID
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
     * Creates a registry containing the PostgreSQL Debezium fallback source.
     */
    public CdcSourceRegistry() {
        this(List.of());
    }

    /**
     * Registers one connector under a unique non-blank ID.
     *
     * @param connector connector to register
     * @throws IllegalArgumentException if the connector is null or its ID is blank or already registered
     */
    public final void register(CdcSourceConnector connector) {
        if (connector == null) {
            throw new IllegalArgumentException("CDC source connector must not be null");
        }
        String id = connector.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CDC source connector id must not be blank");
        }
        if (byId.putIfAbsent(id, connector) != null) {
            throw new IllegalArgumentException("Duplicate CDC source connector id: " + id);
        }
    }

    /**
     * Finds a registered source connector by its exact ID.
     *
     * @param id connector ID
     * @return the registered connector, or empty when the ID is unknown
     */
    public Optional<CdcSourceConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns all registered source connectors in deterministic registration order.
     *
     * @return registered source connectors
     */
    public Collection<CdcSourceConnector> all() {
        return byId.values();
    }
}
