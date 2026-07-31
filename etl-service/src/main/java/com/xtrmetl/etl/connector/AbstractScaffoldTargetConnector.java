package com.xtrmetl.etl.connector;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base for warehouse/BI connectors that are documented but not production-wired.
 */
public abstract class AbstractScaffoldTargetConnector implements TargetConnector {

    private final String id;
    private final String displayName;

    protected AbstractScaffoldTargetConnector(String id, String displayName) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
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
    public void validate(Map<String, String> config) {
        // Scaffolds accept any map; real connectors override with required keys.
        Objects.requireNonNull(config, "config");
    }

    @Override
    public final void write(List<ChangeRecord> batch) {
        throw new UnsupportedOperationException(
                displayName + " connector is a scaffold only; write path is not implemented. "
                        + "See docs/connectors/ for status and planned config."
        );
    }
}
