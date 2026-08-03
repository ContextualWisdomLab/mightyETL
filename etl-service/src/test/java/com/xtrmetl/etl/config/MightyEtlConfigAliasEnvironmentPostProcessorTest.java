package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies dual-read configuration aliases for ETL and connector settings.
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
    void mirrorsModernBatchLimitsToLegacyKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.etl.max-payload-bytes", "2048");
        env.setProperty("mightyetl.etl.max-batch-records", "25");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("2048", aliases.get("xtrmetl.etl.max-payload-bytes"));
        assertEquals("25", aliases.get("xtrmetl.etl.max-batch-records"));
    }

    @Test
    void mirrorsLegacyBatchLimitForModernTooling() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("xtrmetl.etl.max-batch-records", "100");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("100", aliases.get("mightyetl.etl.max-batch-records"));
    }

    @Test
    void modernValueWinsWhenBothNamespacesAreSet() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.etl.max-payload-bytes", "4096");
        env.setProperty("xtrmetl.etl.max-payload-bytes", "1024");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("4096", aliases.get("xtrmetl.etl.max-payload-bytes"));
    }

    @Test
    void emptyWhenUnset() {
        assertTrue(MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(new MockEnvironment()).isEmpty());
    }
}
