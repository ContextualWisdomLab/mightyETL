package com.xtrmetl.etl.config;

import com.xtrmetl.etl.connector.ConnectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies dual-read configuration aliases for ETL and connector settings.
 */
class MightyEtlConfigAliasEnvironmentPostProcessorTest {

    private static final Map<String, String> CONNECTOR_SETTINGS = Map.ofEntries(
            Map.entry("connectors.databricks.enabled", "true"),
            Map.entry("connectors.databricks.host", "workspace.example"),
            Map.entry("connectors.databricks.http-path", "/sql/1.0/warehouses/demo"),
            Map.entry("connectors.databricks.token", "test-databricks-token"),
            Map.entry("connectors.databricks.catalog", "main"),
            Map.entry("connectors.databricks.schema", "analytics"),
            Map.entry("connectors.databricks.table", "events"),
            Map.entry("connectors.databricks.write-mode", "append"),
            Map.entry("connectors.snowflake.enabled", "true"),
            Map.entry("connectors.snowflake.account", "test-account"),
            Map.entry("connectors.snowflake.warehouse", "COMPUTE_WH"),
            Map.entry("connectors.snowflake.database", "ANALYTICS"),
            Map.entry("connectors.snowflake.schema", "PUBLIC"),
            Map.entry("connectors.snowflake.user", "etl_user"),
            Map.entry("connectors.snowflake.password", "test-snowflake-password"),
            Map.entry("connectors.snowflake.private-key", "test-private-key"),
            Map.entry("connectors.snowflake.role", "ETL_ROLE"),
            Map.entry("connectors.snowflake.table", "EVENTS"),
            Map.entry("connectors.snowflake.merge-keys", "id"),
            Map.entry("connectors.qlik-sense.enabled", "true"),
            Map.entry("connectors.qlik-sense.tenant-url", "https://tenant.example"),
            Map.entry("connectors.qlik-sense.api-key", "test-qlik-api-key"),
            Map.entry("connectors.qlik-sense.app-id", "test-app-id"),
            Map.entry("connectors.qlik-sense.mode", "reload-only")
    );

    @Test
    void aliasAllowListMatchesCompleteConnectorBindingSurface() {
        Set<String> expected = new TreeSet<>();
        expected.addAll(writableConnectorKeys("databricks", ConnectorProperties.DatabricksProps.class));
        expected.addAll(writableConnectorKeys("snowflake", ConnectorProperties.SnowflakeProps.class));
        expected.addAll(writableConnectorKeys("qlik-sense", ConnectorProperties.QlikSenseProps.class));

        Set<String> actual = new TreeSet<>();
        MightyEtlConfigAliasEnvironmentPostProcessor.RELATIVE_KEYS.stream()
                .filter(key -> key.startsWith("connectors."))
                .forEach(actual::add);

        assertEquals(
                expected,
                actual,
                "Connector aliases must stay machine-bound to every writable ConnectorProperties setting"
        );
    }

    @Test
    void mirrorsEveryModernConnectorSettingToLegacyConsumers() {
        MockEnvironment env = new MockEnvironment();
        CONNECTOR_SETTINGS.forEach((relative, value) -> env.setProperty("mightyetl." + relative, value));

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        CONNECTOR_SETTINGS.forEach((relative, value) ->
                assertEquals(value, aliases.get("xtrmetl." + relative), relative));
    }

    @Test
    void mirrorsEveryLegacyConnectorSettingForModernTooling() {
        MockEnvironment env = new MockEnvironment();
        CONNECTOR_SETTINGS.forEach((relative, value) -> env.setProperty("xtrmetl." + relative, value));

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        CONNECTOR_SETTINGS.forEach((relative, value) ->
                assertEquals(value, aliases.get("mightyetl." + relative), relative));
    }

    @Test
    void mirrorsModernConnectorFlagsToLegacyKeys() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.connectors.databricks.enabled", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("true", aliases.get("xtrmetl.connectors.databricks.enabled"));
    }

    @Test
    void modernConnectorValueWinsWhenBothNamespacesAreSet() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.connectors.snowflake.password", "modern-test-secret");
        env.setProperty("xtrmetl.connectors.snowflake.password", "legacy-test-secret");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("modern-test-secret", aliases.get("xtrmetl.connectors.snowflake.password"));
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

    /** Returns the environment-property keys represented by writable JavaBean-style setters. */
    private static Set<String> writableConnectorKeys(String connectorId, Class<?> propertiesType) {
        Set<String> keys = new TreeSet<>();
        for (Method method : propertiesType.getMethods()) {
            if (!method.getName().startsWith("set") || method.getName().length() <= 3
                    || method.getParameterCount() != 1 || method.getReturnType() != void.class) {
                continue;
            }
            String property = method.getName().substring(3);
            property = Character.toLowerCase(property.charAt(0)) + property.substring(1);
            String kebab = property.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                    .toLowerCase(Locale.ROOT);
            keys.add("connectors." + connectorId + "." + kebab);
        }
        return keys;
    }

    @Configuration(proxyBeanMethods = false)
    static class AliasOrderProbeConfiguration {
    }
}
