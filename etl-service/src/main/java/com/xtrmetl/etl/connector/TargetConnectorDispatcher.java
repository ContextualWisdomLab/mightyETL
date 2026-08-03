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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Owns target connector dispatch and lifecycle.
 *
 * <p>Supported connectors are opened lazily before their first write, reused for subsequent
 * batches, and closed during application shutdown. Scaffold and unsupported connectors retain
 * fail-closed write behavior. Primary ETL still uses PostgreSQL via {@code EtlService}.</p>
 *
 * <p>A fair read/write lifecycle gate permits concurrent work across different connectors while
 * ensuring shutdown waits for every in-flight write. Each connector has its own monitor so open,
 * write, and close operations for that connector remain ordered even when callers dispatch
 * concurrently.</p>
 */
@Component
public class TargetConnectorDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TargetConnectorDispatcher.class);

    private final TargetConnectorRegistry registry;
    private final ConnectorProperties properties;
    private final ConcurrentMap<String, TargetConnector> openedConnectors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> lifecycleLocks = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycleGate = new ReentrantReadWriteLock(true);
    private boolean closed;

    public TargetConnectorDispatcher(TargetConnectorRegistry registry, ConnectorProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
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
     * opened exactly once per dispatcher lifecycle; an open failure is cleaned up, not cached, and
     * can be retried by a later dispatch. Writes to the same connector are serialized, while
     * independent connectors may progress concurrently. Dispatch is refused after shutdown begins.</p>
     *
     * @param connectorId registered connector identifier
     * @param batch normalized change records to write
     * @throws NullPointerException when {@code connectorId} or {@code batch} is null
     * @throws IllegalArgumentException when the connector identifier is unknown
     * @throws IllegalStateException when the connector is disabled or the dispatcher is closed
     * @throws UnsupportedOperationException when the connector is not production-supported
     */
    public void dispatch(String connectorId, List<ChangeRecord> batch) {
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        Objects.requireNonNull(batch, "batch must not be null");

        Lock dispatchLock = lifecycleGate.readLock();
        dispatchLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Target connector dispatcher is closed");
            }

            TargetConnector connector = registry.find(connectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + connectorId));
            if (!properties.isEnabled(connectorId)) {
                throw new IllegalStateException(
                        "Connector '" + connectorId + "' is disabled. Set xtrmetl.connectors."
                                + connectorId.replace("-", ".")
                                + ".enabled=true only after implementation."
                );
            }

            Map<String, String> config = properties.configMap(connectorId);
            if (connector.status() != ConnectorStatus.SUPPORTED) {
                connector.validate(config);
                throw new UnsupportedOperationException(connector.writeRefusalReason());
            }

            Object connectorLock = lifecycleLocks.computeIfAbsent(
                    connectorId,
                    ignored -> new Object()
            );
            synchronized (connectorLock) {
                ensureOpen(connectorId, connector, config);
                connector.write(batch);
            }
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * Returns connector capability and runtime-open state without exposing configuration secrets.
     *
     * @return stable-order catalog rows for every registered connector
     */
    public List<Map<String, Object>> catalog() {
        Lock catalogLock = lifecycleGate.readLock();
        catalogLock.lock();
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (TargetConnector connector : registry.all()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", connector.id());
                row.put("displayName", connector.displayName());
                row.put("status", connector.status().name());
                row.put("enabled", properties.isEnabled(connector.id()));
                row.put("writable", !closed
                        && connector.status() == ConnectorStatus.SUPPORTED
                        && properties.isEnabled(connector.id()));
                row.put("opened", openedConnectors.get(connector.id()) == connector);
                row.put("requiredConfigKeys", connector.requiredConfigKeys());
                row.put("optionalConfigKeys", connector.optionalConfigKeys());
                row.put("writeRefusalReason", connector.writeRefusalReason());
                row.put("integration", connector.describeIntegration());
                rows.add(row);
            }
            return rows;
        } finally {
            catalogLock.unlock();
        }
    }

    /**
     * Opens a supported connector while its connector-specific monitor is held.
     *
     * <p>If opening fails after allocating partial resources, {@link TargetConnector#close()} is
     * invoked as a best-effort rollback. A cleanup failure is attached to the original exception
     * and never replaces the causal open failure.</p>
     */
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

        try {
            connector.open(config);
        } catch (RuntimeException openFailure) {
            try {
                connector.close();
            } catch (RuntimeException cleanupFailure) {
                openFailure.addSuppressed(cleanupFailure);
                log.warn(
                        "Failed to clean up target connector after open failure id={}",
                        connectorId,
                        cleanupFailure
                );
            }
            throw openFailure;
        }

        openedConnectors.put(connectorId, connector);
        log.info("Opened target connector id={}", connectorId);
    }

    /**
     * Closes every connector successfully opened by this dispatcher.
     *
     * <p>The exclusive lifecycle gate waits for active writes before closing resources, makes the
     * operation idempotent, and permanently refuses later dispatch attempts. Package visibility
     * supports deterministic lifecycle tests; Spring invokes this method at bean destruction.</p>
     */
    @PreDestroy
    void closeOpenedConnectors() {
        Lock shutdownLock = lifecycleGate.writeLock();
        shutdownLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;

            for (Map.Entry<String, TargetConnector> entry
                    : new ArrayList<>(openedConnectors.entrySet())) {
                String connectorId = entry.getKey();
                TargetConnector connector = entry.getValue();
                Object connectorLock = lifecycleLocks.computeIfAbsent(
                        connectorId,
                        ignored -> new Object()
                );
                synchronized (connectorLock) {
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
            lifecycleLocks.clear();
        } finally {
            shutdownLock.unlock();
        }
    }
}
