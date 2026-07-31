package com.xtrmetl.etl.connector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base for warehouse/BI connectors that ship SPI + config validation + catalog hooks
 * but refuse production writes until a real client is wired.
 */
public abstract class AbstractScaffoldTargetConnector implements TargetConnector {

    private final String id;
    private final String displayName;
    private final List<String> requiredConfigKeys;
    private final List<String> optionalConfigKeys;
    private final Map<String, Object> integration;

    protected AbstractScaffoldTargetConnector(
            String id,
            String displayName,
            List<String> requiredConfigKeys,
            List<String> optionalConfigKeys,
            Map<String, Object> integration
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.requiredConfigKeys = List.copyOf(Objects.requireNonNull(requiredConfigKeys, "requiredConfigKeys"));
        this.optionalConfigKeys = List.copyOf(Objects.requireNonNull(optionalConfigKeys, "optionalConfigKeys"));
        this.integration = Map.copyOf(Objects.requireNonNull(integration, "integration"));
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final ConnectorStatus status() {
        return ConnectorStatus.SCAFFOLD;
    }

    @Override
    public final List<String> requiredConfigKeys() {
        return requiredConfigKeys;
    }

    @Override
    public final List<String> optionalConfigKeys() {
        return optionalConfigKeys;
    }

    @Override
    public final String writeRefusalReason() {
        return displayName + " connector is SCAFFOLD: write path refuses until a live client is implemented. "
                + "See docs/connectors/.";
    }

    @Override
    public final Map<String, Object> describeIntegration() {
        Map<String, Object> copy = new LinkedHashMap<>(integration);
        copy.putIfAbsent("mode", "scaffold");
        copy.putIfAbsent("networkIo", false);
        return Map.copyOf(copy);
    }

    @Override
    public void validate(Map<String, String> config) {
        Objects.requireNonNull(config, "config");
        List<String> missing = new ArrayList<>();
        for (String key : requiredConfigKeys) {
            String value = config.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    displayName + " config missing required keys: " + missing
                            + " (status=SCAFFOLD; live write still refused after validation)"
            );
        }
    }

    /**
     * Scaffold open: validate only — no client, no network.
     */
    @Override
    public final void open(Map<String, String> config) {
        validate(config);
    }

    @Override
    public final void write(List<ChangeRecord> batch) {
        Objects.requireNonNull(batch, "batch");
        throw new UnsupportedOperationException(writeRefusalReason());
    }
}
