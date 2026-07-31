package com.xtrmetl.etl.connector;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guards scaffold connectors: {@code enabled=true} without a real write path is refused.
 * Primary ETL still uses PostgreSQL via {@code EtlService}.
 */
@Component
public class TargetConnectorDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TargetConnectorDispatcher.class);

    private final TargetConnectorRegistry registry;
    private final ConnectorProperties properties;

    public TargetConnectorDispatcher(TargetConnectorRegistry registry, ConnectorProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostConstruct
    void logConnectorCatalog() {
        for (TargetConnector connector : registry.all()) {
            boolean enabled = properties.isEnabled(connector.id());
            log.info(
                    "Target connector id={} status={} enabled={} requiredKeys={} (scaffold write path not production-ready)",
                    connector.id(),
                    connector.status(),
                    enabled,
                    connector.requiredConfigKeys()
            );
            if (enabled && connector.status() == ConnectorStatus.SCAFFOLD) {
                log.warn(
                        "Connector '{}' is enabled in config but remains SCAFFOLD — "
                                + "write() will throw. See docs/connectors/",
                        connector.id()
                );
            }
        }
    }

    /**
     * Attempt a batch write. Scaffold + enabled still throws until implemented.
     * When enabled, config is validated first so missing keys fail fast with
     * {@link IllegalArgumentException} before the scaffold write refusal.
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
        // Validate bound YAML/env surface even for scaffolds (integration hook).
        connector.validate(properties.configMap(connectorId));
        if (connector.status() == ConnectorStatus.SCAFFOLD) {
            throw new UnsupportedOperationException(
                    connector.writeRefusalReason()
            );
        }
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
            row.put("writable", connector.status() == ConnectorStatus.SUPPORTED && properties.isEnabled(connector.id()));
            row.put("requiredConfigKeys", connector.requiredConfigKeys());
            row.put("optionalConfigKeys", connector.optionalConfigKeys());
            row.put("writeRefusalReason", connector.writeRefusalReason());
            row.put("integration", connector.describeIntegration());
            rows.add(row);
        }
        return rows;
    }
}
