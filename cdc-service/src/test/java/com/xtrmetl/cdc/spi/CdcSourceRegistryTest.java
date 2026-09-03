package com.xtrmetl.cdc.spi;

import com.xtrmetl.cdc.service.CdcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies source registry composition and the PostgreSQL SPI delegation contract.
 */
class CdcSourceRegistryTest {

    @Test
    void defaultConstructorRegistersPostgresOnly() {
        CdcSourceRegistry registry = new CdcSourceRegistry();

        assertEquals(1, registry.all().size());
        CdcSourceConnector source = registry.find(PostgresDebeziumCdcSource.ID).orElseThrow();
        assertEquals("PostgreSQL (Debezium embedded)", source.displayName());
        assertFalse(source.capabilities().scaffoldOnly());
        assertTrue(source.capabilities().databases().contains("postgresql"));
    }

    @Test
    void postgresSpiStartAndStopDelegateToCdcService() throws Exception {
        CdcService service = mock(CdcService.class);
        ObjectProvider<CdcService> provider = providerFor(service);
        PostgresDebeziumCdcSource source = new PostgresDebeziumCdcSource(provider);

        source.validate(Map.of());
        source.start(Map.of());
        source.stop();

        verify(service).start();
        verify(service).stop();
    }

    @Test
    void mysqlScaffoldIsNotAutoDiscoveredAsAProductionSource() {
        assertFalse(MysqlDebeziumCdcSource.class.isAnnotationPresent(Component.class));
    }

    @Test
    void registersScaffoldSourcesWhenProvidedExplicitly() {
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(
                new PostgresDebeziumCdcSource(),
                new MysqlDebeziumCdcSource(),
                new SqlServerDebeziumCdcSource()
        ));

        assertEquals(3, registry.all().size());
        assertTrue(registry.find(MysqlDebeziumCdcSource.ID).orElseThrow().capabilities().scaffoldOnly());
        assertTrue(registry.find(SqlServerDebeziumCdcSource.ID).orElseThrow().capabilities().scaffoldOnly());
    }

    private static ObjectProvider<CdcService> providerFor(CdcService service) {
        return new ObjectProvider<>() {
            @Override
            public CdcService getObject() {
                return service;
            }

            @Override
            public CdcService getObject(Object... args) {
                return service;
            }

            @Override
            public CdcService getIfAvailable() {
                return service;
            }

            @Override
            public CdcService getIfUnique() {
                return service;
            }
        };
    }
}
