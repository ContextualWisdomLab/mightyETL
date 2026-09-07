package com.xtrmetl.cdc.spi;

import com.xtrmetl.cdc.service.CdcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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
        CdcSourceRegistry sourceRegistry = new CdcSourceRegistry();

        assertEquals(1, sourceRegistry.all().size());
        CdcSourceConnector postgresSource = sourceRegistry.find(PostgresDebeziumCdcSource.SOURCE_ID).orElseThrow();
        assertEquals("PostgreSQL (Debezium embedded)", postgresSource.displayName());
        assertFalse(postgresSource.capabilities().scaffoldOnly());
        assertTrue(postgresSource.capabilities().databases().contains("postgresql"));
    }

    @Test
    void postgresSpiStartAndStopDelegateToCdcService() throws Exception {
        CdcService cdcService = mock(CdcService.class);
        ObjectProvider<CdcService> cdcServiceProvider = providerFor(cdcService);
        PostgresDebeziumCdcSource postgresSource = new PostgresDebeziumCdcSource(cdcServiceProvider);

        postgresSource.validate(Map.of());
        postgresSource.start(Map.of());
        postgresSource.stop();

        verify(cdcService).start();
        verify(cdcService).stop();
    }

    @Test
    void registersScaffoldSourcesWhenProvided() {
        CdcSourceRegistry sourceRegistry = new CdcSourceRegistry(List.of(
                new PostgresDebeziumCdcSource(),
                new MysqlDebeziumCdcSource(),
                new SqlServerDebeziumCdcSource()
        ));

        assertEquals(3, sourceRegistry.all().size());
        assertTrue(sourceRegistry.find(MysqlDebeziumCdcSource.SOURCE_ID).orElseThrow().capabilities().scaffoldOnly());
        assertTrue(sourceRegistry.find(SqlServerDebeziumCdcSource.SOURCE_ID).orElseThrow().capabilities().scaffoldOnly());
    }

    private static ObjectProvider<CdcService> providerFor(CdcService cdcService) {
        return new ObjectProvider<>() {
            @Override
            public CdcService getObject() {
                return cdcService;
            }

            @Override
            public CdcService getObject(Object... args) {
                return cdcService;
            }

            @Override
            public CdcService getIfAvailable() {
                return cdcService;
            }

            @Override
            public CdcService getIfUnique() {
                return cdcService;
            }
        };
    }
}
