package com.xtrmetl.cdc.spi;

import java.util.Map;

/**
 * SPI for CDC capture sources. Production path today is PostgreSQL via Debezium
 * inside {@code CdcService}; additional engines plug in here.
 */
public interface CdcSourceConnector extends AutoCloseable {

    /**
     * Returns the bounded-context-specific CDC source identifier.
     *
     * <p>New organization-owned callers must use this semantic accessor. The generic
     * {@link #id()} method remains only as an SPI compatibility boundary for existing
     * external connector implementations and callers.</p>
     *
     * @return exact CDC source identifier
     */
    default String sourceId() {
        return id();
    }

    /**
     * Legacy compatibility accessor for the historical generic connector identifier.
     *
     * @return exact CDC source identifier
     * @deprecated organization-owned callers must use {@link #sourceId()}
     */
    @Deprecated(forRemoval = false)
    String id();

    String displayName();

    SourceCapabilities capabilities();

    /**
     * Validate source configuration (host, slot, credentials, include lists).
     */
    void validate(Map<String, String> sourceConfig);

    /**
     * Begin capturing changes. Implementations publish through the service pipeline.
     */
    void start(Map<String, String> sourceConfig);

    /**
     * Stop capture gracefully.
     */
    void stop();

    @Override
    default void close() {
        stop();
    }
}
