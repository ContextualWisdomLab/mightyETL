package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects configured CDC source identity from ambiguous duplicate declarations.
 */
class CdcSourceFactoryIdentityContractTest {

    @Test
    void duplicateConfiguredSourceIdsFailClosed() {
        CdcSourceFactory factory = factory();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> factory.describeConfigured(List.of(
                        new CdcSourceFactory.SourceSpec("pg-main", "postgres-debezium", true),
                        new CdcSourceFactory.SourceSpec("pg-main", "postgres-debezium", false)
                ))
        );

        assertTrue(failure.getMessage().contains("pg-main"));
    }

    @Test
    void repeatedSourceTypeWithDistinctIdsRemainsValid() {
        CdcSourceFactory factory = factory();

        assertDoesNotThrow(() -> factory.describeConfigured(List.of(
                new CdcSourceFactory.SourceSpec("pg-primary", "postgres-debezium", true),
                new CdcSourceFactory.SourceSpec("pg-secondary", "postgres-debezium", false)
        )));
    }

    private static CdcSourceFactory factory() {
        return new CdcSourceFactory(new CdcSourceRegistry(List.of(new PostgresDebeziumCdcSource())));
    }
}
