package com.xtrmetl.cdc.spi;

import com.xtrmetl.cdc.service.CdcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
    void postgresSpiStartDelegatesToCdcService() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        CdcService service = new CdcService(kafka, false);
        // Pre-seed a mock engine so start does not need PG env
        ReflectionSeed.seedEngine(service);

        ObjectProvider<CdcService> provider = new ObjectProvider<>() {
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

        PostgresDebeziumCdcSource source = new PostgresDebeziumCdcSource(provider);
        source.validate(Map.of());
        source.start(Map.of());
        assertTrue(service.isRunning());
        source.stop();
    }

    @Test
    void registersScaffoldSourcesWhenProvided() {
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(
                new PostgresDebeziumCdcSource(),
                new MysqlDebeziumCdcSource(),
                new SqlServerDebeziumCdcSource()
        ));

        assertEquals(3, registry.all().size());
        assertTrue(registry.find(MysqlDebeziumCdcSource.ID).orElseThrow().capabilities().scaffoldOnly());
        assertTrue(registry.find(SqlServerDebeziumCdcSource.ID).orElseThrow().capabilities().scaffoldOnly());
    }

    /** Avoid pulling ReflectionTestUtils dependency issues — tiny helper. */
    static final class ReflectionSeed {
        static void seedEngine(CdcService service) {
            @SuppressWarnings("unchecked")
            io.debezium.engine.DebeziumEngine<io.debezium.engine.ChangeEvent<String, String>> engine =
                    (io.debezium.engine.DebeziumEngine<io.debezium.engine.ChangeEvent<String, String>>)
                            mock(io.debezium.engine.DebeziumEngine.class);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "debeziumEngine", engine);
        }
    }
}
