package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies configured CDC source resolution against the production-visible registry contract.
 */
class CdcSourceFactoryTest {

    @Test
    void resolvesPostgresDebezium() {
        CdcSourceFactory factory = productionVisibleFactory();
        assertTrue(factory.resolve("postgres-debezium").isPresent());
        assertTrue(factory.resolve("POSTGRES-DEBEZIUM").isPresent());
        assertTrue(factory.resolve("mysql-debezium").isPresent());
        assertTrue(factory.resolve("oracle-debezium").isEmpty());
        assertTrue(factory.resolve("sqlserver-debezium").isEmpty());
    }

    @Test
    void describeConfiguredMarksUnknownScaffoldAndRetiredSqlServerTypes() {
        CdcSourceFactory factory = productionVisibleFactory();
        List<Map<String, Object>> rows = factory.describeConfigured(List.of(
                new CdcSourceFactory.SourceSpec("pg-main", "postgres-debezium", true),
                new CdcSourceFactory.SourceSpec("mysql-1", "mysql-debezium", true),
                new CdcSourceFactory.SourceSpec("ora-1", "oracle-debezium", true),
                new CdcSourceFactory.SourceSpec("sqlserver-1", "sqlserver-debezium", true)
        ));

        assertEquals(4, rows.size());
        assertEquals(true, rows.get(0).get("registered"));
        assertFalse((Boolean) rows.get(0).get("scaffoldOnly"));
        assertEquals(true, rows.get(1).get("registered"));
        assertTrue((Boolean) rows.get(1).get("scaffoldOnly"));
        assertEquals(false, rows.get(2).get("registered"));
        assertEquals("unknown_source_type", rows.get(2).get("error"));
        assertEquals("sqlserver-1", rows.get(3).get("id"));
        assertEquals("sqlserver-debezium", rows.get(3).get("type"));
        assertEquals(true, rows.get(3).get("enabled"));
        assertEquals(false, rows.get(3).get("registered"));
        assertTrue((Boolean) rows.get(3).get("scaffoldOnly"));
        assertEquals("unknown_source_type", rows.get(3).get("error"));
    }

    private static CdcSourceFactory productionVisibleFactory() {
        return new CdcSourceFactory(new CdcSourceRegistry(List.of(
                new PostgresDebeziumCdcSource(),
                new MysqlDebeziumCdcSource()
        )));
    }
}
