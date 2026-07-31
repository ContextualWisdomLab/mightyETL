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
 */
@Component
public class CdcSourceRegistry {

    private final Map<String, CdcSourceConnector> byId = new LinkedHashMap<>();

    public CdcSourceRegistry(ObjectProvider<CdcSourceConnector> connectors) {
        connectors.orderedStream().forEach(this::register);
        if (byId.isEmpty()) {
            // Unit tests / non-Spring construction
            register(new PostgresDebeziumCdcSource());
        }
    }

    /**
     * Explicit list for tests.
     */
    public CdcSourceRegistry(List<CdcSourceConnector> connectors) {
        if (connectors != null) {
            connectors.forEach(this::register);
        }
        if (byId.isEmpty()) {
            register(new PostgresDebeziumCdcSource());
        }
    }

    public CdcSourceRegistry() {
        this(List.of());
    }

    public final void register(CdcSourceConnector connector) {
        byId.put(connector.id(), connector);
    }

    public Optional<CdcSourceConnector> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<CdcSourceConnector> all() {
        return byId.values();
    }
}
