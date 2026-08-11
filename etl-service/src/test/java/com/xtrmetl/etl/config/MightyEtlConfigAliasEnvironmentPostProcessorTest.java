package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void modernConfigDataWinsOverLegacyConfigData(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, """
                mightyetl.etl.max-payload-bytes=4096
                xtrmetl.etl.max-payload-bytes=1024
                """);

        SpringApplication application = new SpringApplication(AliasOrderProbeConfiguration.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.config.location=" + configFile.toUri()
        )) {
            assertTrue(context.getEnvironment().getPropertySources().contains(
                    MightyEtlConfigAliasEnvironmentPostProcessor.PROPERTY_SOURCE_NAME
            ));
            assertEquals(
                    "4096",
                    context.getEnvironment().getProperty("mightyetl.etl.max-payload-bytes")
            );
            assertEquals(
                    "4096",
                    context.getEnvironment().getProperty("xtrmetl.etl.max-payload-bytes")
            );
        }
    }

    @Test
    void emptyWhenUnset() {
        assertTrue(MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(new MockEnvironment()).isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    static class AliasOrderProbeConfiguration {
    }
}
