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
    void mirrorsModernDurableIntakeFlagToTheLegacyControllerCondition() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.etl.jobs.intake-enabled", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("true", aliases.get("xtrmetl.etl.jobs.intake-enabled"));
    }

    @Test
    void mirrorsEveryModernDurableWorkerSettingToLegacyConsumers() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.etl.jobs.worker.enabled", "true");
        env.setProperty("mightyetl.etl.jobs.worker.fixed-delay-milliseconds", "2500");
        env.setProperty("mightyetl.etl.jobs.worker.initial-delay-milliseconds", "1000");
        env.setProperty("mightyetl.etl.jobs.worker.lease-duration-seconds", "120");
        env.setProperty("mightyetl.etl.jobs.worker.max-attempts", "5");
        env.setProperty("mightyetl.etl.jobs.worker.lease-owner-id", "worker-primary");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("true", aliases.get("xtrmetl.etl.jobs.worker.enabled"));
        assertEquals(
                "2500",
                aliases.get("xtrmetl.etl.jobs.worker.fixed-delay-milliseconds")
        );
        assertEquals(
                "1000",
                aliases.get("xtrmetl.etl.jobs.worker.initial-delay-milliseconds")
        );
        assertEquals(
                "120",
                aliases.get("xtrmetl.etl.jobs.worker.lease-duration-seconds")
        );
        assertEquals("5", aliases.get("xtrmetl.etl.jobs.worker.max-attempts"));
        assertEquals(
                "worker-primary",
                aliases.get("xtrmetl.etl.jobs.worker.lease-owner-id")
        );
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
