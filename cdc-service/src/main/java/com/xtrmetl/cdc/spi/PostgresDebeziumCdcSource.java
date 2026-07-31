package com.xtrmetl.cdc.spi;

import com.xtrmetl.cdc.service.CdcService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for the live Debezium PostgreSQL capture path owned by {@link CdcService}.
 *
 * <p>{@link #start(Map)} / {@link #stop()} delegate to the service when available so the SPI
 * is a real control surface for the supported source type.</p>
 */
@Component
public final class PostgresDebeziumCdcSource implements CdcSourceConnector {

    public static final String ID = "postgres-debezium";

    private final ObjectProvider<CdcService> cdcService;

    public PostgresDebeziumCdcSource(ObjectProvider<CdcService> cdcService) {
        this.cdcService = cdcService;
    }

    /**
     * Test / registry fallback without Spring (no live {@link CdcService}).
     */
    public PostgresDebeziumCdcSource() {
        this(new ObjectProvider<>() {
            @Override
            public CdcService getObject() {
                return null;
            }

            @Override
            public CdcService getObject(Object... args) {
                return null;
            }

            @Override
            public CdcService getIfAvailable() {
                return null;
            }

            @Override
            public CdcService getIfUnique() {
                return null;
            }
        });
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "PostgreSQL (Debezium embedded)";
    }

    @Override
    public SourceCapabilities capabilities() {
        return new SourceCapabilities("debezium-embedded", Set.of("postgresql"), false);
    }

    @Override
    public void validate(Map<String, String> config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
    }

    @Override
    public void start(Map<String, String> config) {
        validate(config);
        CdcService service = cdcService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException(
                    "CdcService is not available; cannot start postgres-debezium via SPI"
            );
        }
        service.start();
    }

    @Override
    public void stop() {
        CdcService service = cdcService.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.stop();
        } catch (IOException e) {
            throw new IllegalStateException("Error stopping CDC via SPI", e);
        }
    }
}
