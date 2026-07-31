package com.xtrmetl.cdc.spi;

import java.util.Map;

/**
 * SPI for CDC capture sources. Production path today is PostgreSQL via Debezium
 * inside {@code CdcService}; additional engines plug in here.
 */
public interface CdcSourceConnector extends AutoCloseable {

    String id();

    String displayName();

    SourceCapabilities capabilities();

    /**
     * Validate source configuration (host, slot, credentials, include lists).
     */
    void validate(Map<String, String> config);

    /**
     * Begin capturing changes. Implementations publish through the service pipeline.
     */
    void start(Map<String, String> config);

    /**
     * Stop capture gracefully.
     */
    void stop();

    @Override
    default void close() {
        stop();
    }
}
