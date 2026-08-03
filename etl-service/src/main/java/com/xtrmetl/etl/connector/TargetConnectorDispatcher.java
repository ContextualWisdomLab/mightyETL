package com.xtrmetl.etl.connector;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Owns target connector dispatch and lifecycle.
 *
 * <p>Supported connectors are opened lazily before their first write, reused for subsequent
 * batches, and closed during application shutdown. Scaffold and unsupported connectors retain
 * fail-closed write behavior. Primary ETL still uses PostgreSQL via {@code EtlService}.</p>
 */
@Component
public class TargetConnectorDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TargetConnectorDispatcher.class);

    private final TargetConnectorRegistry registry;
    private final ConnectorProperties properties;
    private final ConcurrentMap<String, TargetConnector> openedConnectors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> lifecycleLocks = new ConcurrentHashMap<>();

    public TargetConnectorDispatcher(TargetConnectorRegistry registry, ConnectorProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostConstruct
    void logConnectorCatalog() {
        for (TargetConnector connector : registry.all()) {
            boolean enabled = properties.isEnabled(connector.id());
            log.info(
                    "Target connector id={} status={} enabled={} requiredKeys={}",
                    connector.id(),
                    connector.status(),
                    enabled,
                    connector.requiredConfigKeys()
            );
            if (enabled && connector.status() != ConnectorStatus.SUPPORTED) {
                log.warn(
                        "Connector '{}' is enabled but status={} — write() will be refused. "
                                + "See docs/connectors/",
                        connector.id(),
                        connector.status()
                );
            }
        }
    }

    /**
     * Writes a batch through an enabled, supported connector.
     *
     * <p>Non-supported connectors validate their bound configuration first so operators receive
     * missing-key diagnostics before the implementation-status refusal. Supported connectors are
     * opened exactly once per dispatcher lifecycle; an open failure is not cached and can be
     * retried by a later dispatch.</p>
     */
    public void dispatch(String connectorId, List<ChangeRecord> batch) {
        TargetConnector connector = registry.find(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + connectorId));
        if (!properties.isEnabled(connectorId)) {
            throw new IllegalStateException(
                    "Connector '" + connectorId + "' is disabled. Set xtrmetl.connectors."
                            + connectorId.replace("-", ".") + ".enabled=true only after implementation."
            );
        }

        Map<String, String> config = properties.configMap(connectorId);
        if (connector.status() != ConnectorStatus.SUPPORTED) {
            connector.validate(config);
            throw new UnsupportedOperationException(connector.writeRefusalReason());
        }

        ensureOpen(connectorId, connector, config);
        connector.write(batch);
    }

    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TargetConnector connector : registry.all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", connector.id());
            row.put("displayName", connector.displayName());
            row.put("status", connector.status().name());
            row.put("enabled", properties.isEnabled(connector.id()));
            row.put("writable", connector.status() == ConnectorStatus.SUPPORTED
                    && properties.isEnabled(connector.id()));
            row.put("opened", openedConnectors.get(connector.id()) == connector);
            row.put("requiredConfigKeys", connector.requiredConfigKeys());
            row.put("optionalConfigKeys", connector.optionalConfigKeys());
            row.put("writeRefusalReason", connector.writeRefusalReason());
            row.put("integration", connector.describeIntegration());
            rows.add(row);
        }
        return rows;
    }

    private void ensureOpen(
            String connectorId,
            TargetConnector connector,
            Map<String, String> config
    ) {
        TargetConnector active = openedConnectors.get(connectorId);
        if (active == connector) {
            return;
        }
        if (active != null) {
            throw new IllegalStateException(
                    "Connector registry entry changed after open: " + connectorId
            );
        }

        Object lock = lifecycleLocks.computeIfAbsent(connectorId, ignored -> new Object());
        synchronized (lock) {
            active = openedConnectors.get(connectorId);
            if (active == connector) {
                return;
            }
            if (active != null) {
                throw new IllegalStateException(
                        "Connector registry entry changed after open: " + connectorId
                );
            }

            connector.open(config);
            openedConnectors.put(connectorId, connector);
            log.info("Opened target connector id={}", connectorId);
        }
    }

    /**
     * Closes every connector successfully opened by this dispatcher. Package visibility supports
     * deterministic lifecycle tests; Spring invokes the same method at bean destruction.
     */
    @PreDestroy
    void closeOpenedConnectors() {
        for (Map.Entry<String, TargetConnector> entry
                : new ArrayList<>(openedConnectors.entrySet())) {
            String connectorId = entry.getKey();
            TargetConnector connector = entry.getValue();
            Object lock = lifecycleLocks.computeIfAbsent(connectorId, ignored -> new Object());
            synchronized (lock) {
                if (!openedConnectors.remove(connectorId, connector)) {
                    continue;
                }
                try {
                    connector.close();
                    log.info("Closed target connector id={}", connectorId);
                } catch (RuntimeException exception) {
                    log.error("Failed to close target connector id={}", connectorId, exception);
                }
            }
        }
    }
}
