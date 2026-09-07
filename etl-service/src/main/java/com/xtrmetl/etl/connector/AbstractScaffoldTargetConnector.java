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

    private final String targetId;
    private final String displayName;
    private final List<String> requiredConfigKeys;
    private final List<String> optionalConfigKeys;
    private final Map<String, Object> integrationMetadata;

    protected AbstractScaffoldTargetConnector(
            String targetId,
            String displayName,
            List<String> requiredConfigKeys,
            List<String> optionalConfigKeys,
            Map<String, Object> integrationMetadata
    ) {
        this.targetId = Objects.requireNonNull(targetId, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.requiredConfigKeys = List.copyOf(Objects.requireNonNull(requiredConfigKeys, "requiredConfigKeys"));
        this.optionalConfigKeys = List.copyOf(Objects.requireNonNull(optionalConfigKeys, "optionalConfigKeys"));
        this.integrationMetadata = Map.copyOf(Objects.requireNonNull(integrationMetadata, "integration"));
    }

    @Override
    public final String targetId() {
        return targetId;
    }

    /**
     * @deprecated compatibility alias; organization-owned callers use {@link #targetId()}
     */
    @Override
    @Deprecated(forRemoval = false)
    public final String id() {
        return targetId();
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
        Map<String, Object> integrationDescription = new LinkedHashMap<>(integrationMetadata);
        integrationDescription.putIfAbsent("mode", "scaffold");
        integrationDescription.putIfAbsent("networkIo", false);
        return Map.copyOf(integrationDescription);
    }

    @Override
    public void validate(Map<String, String> targetConfig) {
        Objects.requireNonNull(targetConfig, "config");
        List<String> missingConfigKeys = new ArrayList<>();
        for (String configKey : requiredConfigKeys) {
            String configValue = targetConfig.get(configKey);
            if (configValue == null || configValue.isBlank()) {
                missingConfigKeys.add(configKey);
            }
        }
        if (!missingConfigKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    displayName + " config missing required keys: " + missingConfigKeys
                            + " (status=SCAFFOLD; live write still refused after validation)"
            );
        }
    }

    /**
     * Scaffold open: validate only — no client, no network.
     */
    @Override
    public final void open(Map<String, String> targetConfig) {
        validate(targetConfig);
    }

    @Override
    public final void write(List<ChangeRecord> changeBatch) {
        Objects.requireNonNull(changeBatch, "batch");
        throw new UnsupportedOperationException(writeRefusalReason());
    }
}
