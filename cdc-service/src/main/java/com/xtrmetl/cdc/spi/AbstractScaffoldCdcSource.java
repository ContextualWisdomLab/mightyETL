package com.xtrmetl.cdc.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base for CDC source types that are registered for discovery but not production-wired.
 */
public abstract class AbstractScaffoldCdcSource implements CdcSourceConnector {

    private final String sourceId;
    private final String displayName;
    private final String sourceEngine;
    private final Set<String> supportedDatabases;

    protected AbstractScaffoldCdcSource(
            String sourceId,
            String displayName,
            String sourceEngine,
            Set<String> supportedDatabases
    ) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.sourceEngine = Objects.requireNonNull(sourceEngine, "sourceEngine");
        this.supportedDatabases = Set.copyOf(supportedDatabases);
    }

    @Override
    public final String sourceId() {
        return sourceId;
    }

    /**
     * @deprecated compatibility alias; organization-owned callers use {@link #sourceId()}
     */
    @Override
    @Deprecated(forRemoval = false)
    public final String id() {
        return sourceId();
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final SourceCapabilities capabilities() {
        return new SourceCapabilities(sourceEngine, supportedDatabases, true);
    }

    @Override
    public void validate(Map<String, String> sourceConfig) {
        Objects.requireNonNull(sourceConfig, "sourceConfig");
    }

    @Override
    public final void start(Map<String, String> sourceConfig) {
        throw new UnsupportedOperationException(
                displayName + " is a scaffold source only (no connector dependency wired). "
                        + "See docs/cdc/any-to-any-cdc.md"
        );
    }

    @Override
    public void stop() {
        // no-op
    }
}
