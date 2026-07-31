package com.xtrmetl.etl.connector;

import java.util.List;
import java.util.Map;

/**
 * SPI for loading normalized change records into an external target system.
 *
 * <p>Scaffold connectors must report {@link ConnectorStatus#SCAFFOLD} and must not
 * silently drop data: {@link #write(List)} should throw until implemented.</p>
 */
public interface TargetConnector extends AutoCloseable {

    String id();

    String displayName();

    ConnectorStatus status();

    /**
     * Config keys that must be non-blank before {@link #open(Map)} / a future live write path.
     * Empty when the connector has no config contract yet.
     */
    default List<String> requiredConfigKeys() {
        return List.of();
    }

    /**
     * Documented optional keys (binding surface for YAML / env).
     */
    default List<String> optionalConfigKeys() {
        return List.of();
    }

    /**
     * Human-readable reason writes are refused (scaffolds) or empty when writable.
     */
    default String writeRefusalReason() {
        if (status() == ConnectorStatus.SUPPORTED) {
            return "";
        }
        return displayName() + " write path is not production-wired; status=" + status();
    }

    /**
     * Integration surface metadata for catalog/ops (no network I/O).
     */
    default Map<String, Object> describeIntegration() {
        return Map.of(
                "client", "none",
                "mode", status() == ConnectorStatus.SUPPORTED ? "live" : "scaffold"
        );
    }

    /**
     * Validate configuration before open. Implementations should fail fast on missing secrets.
     */
    void validate(Map<String, String> config);

    /**
     * Establish client resources (connections, tokens). No-op allowed for pure scaffolds.
     */
    default void open(Map<String, String> config) {
        validate(config);
    }

    /**
     * Write a batch of change records. Scaffold implementations must throw
     * {@link UnsupportedOperationException}.
     */
    void write(List<ChangeRecord> batch);

    @Override
    default void close() {
        // no-op
    }
}
