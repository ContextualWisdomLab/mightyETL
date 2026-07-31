package com.xtrmetl.cdc.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base for CDC source types that are registered for discovery but not production-wired.
 */
public abstract class AbstractScaffoldCdcSource implements CdcSourceConnector {

    private final String id;
    private final String displayName;
    private final String engine;
    private final Set<String> databases;

    protected AbstractScaffoldCdcSource(String id, String displayName, String engine, Set<String> databases) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.databases = Set.copyOf(databases);
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
    public final SourceCapabilities capabilities() {
        return new SourceCapabilities(engine, databases, true);
    }

    @Override
    public void validate(Map<String, String> config) {
        Objects.requireNonNull(config, "config");
    }

    @Override
    public final void start(Map<String, String> config) {
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
