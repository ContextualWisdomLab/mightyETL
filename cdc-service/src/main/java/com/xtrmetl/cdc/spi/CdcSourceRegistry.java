package com.xtrmetl.cdc.spi;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of CDC source connector types discovered as Spring beans, plus a safe
 * fallback for unit tests without a Spring context.
 *
 * <p>Connector identifiers are registration authority. Null connectors, blank
 * identifiers, and duplicate identifiers are rejected before the registry is
 * mutated so one implementation cannot silently replace another.</p>
 */
@Component
public class CdcSourceRegistry {

    private final Map<String, CdcSourceConnector> byId = new LinkedHashMap<>();

    /**
     * Builds a registry from Spring-discovered connector beans.
     *
     * @param connectors provider of discovered source connectors
     */
    public CdcSourceRegistry(ObjectProvider<CdcSourceConnector> connectors) {
        connectors.orderedStream().forEach(this::register);
        if (byId.isEmpty()) {
            // Unit tests / non-Spring construction
            register(new PostgresDebeziumCdcSource());
        }
    }

    /**
     * Builds a registry from an explicit connector list, primarily for tests and
     * standalone embedding.
     *
     * @param connectors source connectors to register; a null list is treated as empty
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
     * Builds a standalone registry with the built-in PostgreSQL/Debezium source.
     */
    public CdcSourceRegistry() {
        this(List.of());
    }

    /**
     * Registers a CDC source connector without permitting ambiguous authority.
     *
     * @param connector connector to register
     * @throws IllegalArgumentException when the connector is null, its identifier
     *                                  is blank, or its identifier is already registered
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
     * Finds a registered source connector by its exact identifier.
     *
     * @param id connector identifier
     * @return the connector when registered, otherwise empty
     */
    public Optional<CdcSourceConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns an unmodifiable live view of all registered source connectors in registration order.
     *
     * @return unmodifiable registered source connectors
     */
    public Collection<CdcSourceConnector> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
