package com.xtrmetl.cdc.spi;

import java.util.Objects;
import java.util.Set;

/**
 * Declares what a {@link CdcSourceConnector} can capture.
 */
public final class SourceCapabilities {

    private final String engine;
    private final Set<String> databases;
    private final boolean scaffoldOnly;

    public SourceCapabilities(String engine, Set<String> databases, boolean scaffoldOnly) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.databases = Set.copyOf(databases);
        this.scaffoldOnly = scaffoldOnly;
    }

    public String engine() {
        return engine;
    }

    public Set<String> databases() {
        return databases;
    }

    public boolean scaffoldOnly() {
        return scaffoldOnly;
    }
}
