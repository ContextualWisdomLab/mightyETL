package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies preferred mightyETL keys and compatibility xtrmetl keys remain dual-readable.
 */
class MightyEtlConfigAliasEnvironmentPostProcessorTest {

    @Test
    void mirrorsModernConnectorFlagsToLegacyKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.connectors.databricks.enabled", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("true", aliases.get("xtrmetl.connectors.databricks.enabled"));
    }

    @Test
    void mirrorsModernProcessingLimitsToLegacyKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.etl.max-batch-records", "250");
        env.setProperty("mightyetl.etl.max-concurrency", "4");
        env.setProperty("mightyetl.etl.queue-capacity", "128");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("250", aliases.get("xtrmetl.etl.max-batch-records"));
        assertEquals("4", aliases.get("xtrmetl.etl.max-concurrency"));
        assertEquals("128", aliases.get("xtrmetl.etl.queue-capacity"));
    }

    @Test
    void modernProcessingLimitWinsWhenBothPrefixesAreSet() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.etl.max-batch-records", "500");
        env.setProperty("xtrmetl.etl.max-batch-records", "100");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("500", aliases.get("xtrmetl.etl.max-batch-records"));
    }

    @Test
    void mirrorsLegacyProcessingLimitToModernKeyWhenModernIsUnset() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("xtrmetl.etl.queue-capacity", "64");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("64", aliases.get("mightyetl.etl.queue-capacity"));
    }

    @Test
    void emptyWhenUnset() {
        assertTrue(MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(new MockEnvironment()).isEmpty());
    }
}
