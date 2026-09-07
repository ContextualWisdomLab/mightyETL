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

    private final TargetConnectorRegistry targetRegistry;
    private final ConnectorProperties connectorProperties;
    private final ConcurrentMap<String, TargetConnector> openedConnectors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> lifecycleLocks = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycleGate = new ReentrantReadWriteLock(true);
    private boolean dispatcherClosed;

    public TargetConnectorDispatcher(
            TargetConnectorRegistry targetRegistry,
            ConnectorProperties connectorProperties
    ) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry, "registry must not be null");
        this.connectorProperties = Objects.requireNonNull(connectorProperties, "properties must not be null");
    }

    @PostConstruct
    void logConnectorCatalog() {
        for (TargetConnector targetConnector : targetRegistry.all()) {
            boolean connectorEnabled = connectorProperties.isEnabled(targetConnector.targetId());
            log.info(
                    "Target connector id={} status={} enabled={} requiredKeys={}",
                    targetConnector.targetId(),
                    targetConnector.status(),
                    connectorEnabled,
                    targetConnector.requiredConfigKeys()
            );
            if (connectorEnabled && targetConnector.status() != ConnectorStatus.SUPPORTED) {
                log.warn(
                        "Connector '{}' is enabled but status={} — write() will be refused. "
                                + "See docs/connectors/",
                        targetConnector.targetId(),
                        targetConnector.status()
                );
            }
        }
    }

    /**
     * Writes a batch through an enabled, supported connector.
     *
     * <p>Non-supported connectors validate their bound configuration first so operators receive
     * missing-key diagnostics before the implementation-status refusal. Supported connectors are
     * validated and opened exactly once per dispatcher lifecycle; an open failure is cleaned up,
     * not cached, and can be retried by a later dispatch. Writes to the same connector are
     * serialized, while independent connectors may progress concurrently. Dispatch is refused
     * after shutdown begins.</p>
     *
     * @param connectorId registered connector identifier
     * @param changeBatch normalized change records to write
     * @throws NullPointerException when {@code connectorId} or {@code changeBatch} is null
     * @throws IllegalArgumentException when the connector identifier is unknown
     * @throws IllegalStateException when the connector is disabled or the dispatcher is closed
     * @throws UnsupportedOperationException when the connector is not production-supported
     */
    public void dispatch(String connectorId, List<ChangeRecord> changeBatch) {
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        Objects.requireNonNull(changeBatch, "batch must not be null");

        Lock dispatchLock = lifecycleGate.readLock();
        dispatchLock.lock();
        try {
            if (dispatcherClosed) {
                throw new IllegalStateException("Target connector dispatcher is closed");
            }

            TargetConnector targetConnector = targetRegistry.find(connectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + connectorId));
            if (!connectorProperties.isEnabled(connectorId)) {
                throw new IllegalStateException(
                        "Connector '" + connectorId + "' is disabled. Set xtrmetl.connectors."
                                + connectorId.replace("-", ".")
                                + ".enabled=true only after implementation."
                );
            }

            Map<String, String> targetConfig = connectorProperties.configMap(connectorId);
            if (targetConnector.status() != ConnectorStatus.SUPPORTED) {
                targetConnector.validate(targetConfig);
                throw new UnsupportedOperationException(targetConnector.writeRefusalReason());
            }

            Object targetLock = lifecycleLocks.computeIfAbsent(
                    connectorId,
                    ignored -> new Object()
            );
            synchronized (targetLock) {
                ensureOpen(connectorId, targetConnector, targetConfig);
                targetConnector.write(changeBatch);
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
            List<Map<String, Object>> catalogRows = new ArrayList<>();
            for (TargetConnector targetConnector : targetRegistry.all()) {
                Map<String, Object> catalogRow = new LinkedHashMap<>();
                catalogRow.put("id", targetConnector.targetId());
                catalogRow.put("displayName", targetConnector.displayName());
                catalogRow.put("status", targetConnector.status().name());
                catalogRow.put("enabled", connectorProperties.isEnabled(targetConnector.targetId()));
                catalogRow.put("writable", !dispatcherClosed
                        && targetConnector.status() == ConnectorStatus.SUPPORTED
                        && connectorProperties.isEnabled(targetConnector.targetId()));
                catalogRow.put("opened", openedConnectors.get(targetConnector.targetId()) == targetConnector);
                catalogRow.put("requiredConfigKeys", targetConnector.requiredConfigKeys());
                catalogRow.put("optionalConfigKeys", targetConnector.optionalConfigKeys());
                catalogRow.put("writeRefusalReason", targetConnector.writeRefusalReason());
                catalogRow.put("integration", targetConnector.describeIntegration());
                catalogRows.add(catalogRow);
            }
            return catalogRows;
        } finally {
            catalogLock.unlock();
        }
    }

    /**
     * Validates and opens a supported connector while its connector-specific monitor is held.
     *
     * <p>If opening fails after allocating partial resources, {@link TargetConnector#close()} is
     * invoked as a best-effort rollback. A cleanup failure is attached to the original exception
     * and never replaces the causal open failure.</p>
     */
    private void ensureOpen(
            String connectorId,
            TargetConnector targetConnector,
            Map<String, String> targetConfig
    ) {
        TargetConnector activeConnector = openedConnectors.get(connectorId);
        if (activeConnector == targetConnector) {
            return;
        }
        if (activeConnector != null) {
            throw new IllegalStateException(
                    "Connector registry entry changed after open: " + connectorId
            );
        }

        targetConnector.validate(targetConfig);
        try {
            targetConnector.open(targetConfig);
        } catch (RuntimeException openFailure) {
            try {
                targetConnector.close();
            } catch (RuntimeException cleanupFailure) {
                openFailure.addSuppressed(cleanupFailure);
                log.warn("Failed to clean up target connector after open failure id={}", connectorId);
            }
            throw openFailure;
        }

        openedConnectors.put(connectorId, targetConnector);
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
            if (dispatcherClosed) {
                return;
            }
            dispatcherClosed = true;

            for (Map.Entry<String, TargetConnector> connectorEntry
                    : new ArrayList<>(openedConnectors.entrySet())) {
                String connectorId = connectorEntry.getKey();
                TargetConnector targetConnector = connectorEntry.getValue();
                Object targetLock = lifecycleLocks.computeIfAbsent(
                        connectorId,
                        ignored -> new Object()
                );
                synchronized (targetLock) {
                    if (!openedConnectors.remove(connectorId, targetConnector)) {
                        continue;
                    }
                    try {
                        targetConnector.close();
                        log.info("Closed target connector id={}", connectorId);
                    } catch (RuntimeException closeFailure) {
                        log.error("Failed to close target connector id={}", connectorId);
                    }
                }
            }
            lifecycleLocks.clear();
        } finally {
            shutdownLock.unlock();
        }
    }
}
