package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdcSourceFactoryTest {

    @Test
    void resolvesPostgresDebezium() {
        CdcSourceFactory factory = new CdcSourceFactory(new CdcSourceRegistry(List.of(
                new PostgresDebeziumCdcSource(),
                new MysqlDebeziumCdcSource()
        )));
        assertTrue(factory.resolve("postgres-debezium").isPresent());
        assertTrue(factory.resolve("POSTGRES-DEBEZIUM").isPresent());
        assertTrue(factory.resolve("mysql-debezium").isPresent());
        assertTrue(factory.resolve("oracle-debezium").isEmpty());
    }

    @Test
    void describeConfiguredMarksUnknownAndScaffoldTypes() {
        CdcSourceFactory factory = new CdcSourceFactory(new CdcSourceRegistry(List.of(
                new PostgresDebeziumCdcSource(),
                new MysqlDebeziumCdcSource()
        )));
        List<Map<String, Object>> rows = factory.describeConfigured(List.of(
                new CdcSourceFactory.SourceSpec("pg-main", "postgres-debezium", true),
                new CdcSourceFactory.SourceSpec("mysql-1", "mysql-debezium", true),
                new CdcSourceFactory.SourceSpec("ora-1", "oracle-debezium", true)
        ));

        assertEquals(3, rows.size());
        assertEquals(true, rows.get(0).get("registered"));
        assertFalse((Boolean) rows.get(0).get("scaffoldOnly"));
        assertEquals(true, rows.get(1).get("registered"));
        assertTrue((Boolean) rows.get(1).get("scaffoldOnly"));
        assertEquals(false, rows.get(2).get("registered"));
        assertEquals("unknown_source_type", rows.get(2).get("error"));
    }
}
