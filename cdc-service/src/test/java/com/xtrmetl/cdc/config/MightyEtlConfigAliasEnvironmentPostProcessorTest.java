package com.xtrmetl.cdc.config;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MightyEtlConfigAliasEnvironmentPostProcessorTest {

    @Test
    void modernPrefixWinsAndMirrorsToLegacy() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.cdc.autostart", "false");
        env.setProperty("xtrmetl.cdc.autostart", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("false", aliases.get("xtrmetl.cdc.autostart"));
        assertFalse(aliases.containsKey("mightyetl.cdc.autostart"));
    }

    @Test
    void legacyOnlyIsMirroredToModern() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("xtrmetl.replica.enabled", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("true", aliases.get("mightyetl.replica.enabled"));
        assertFalse(aliases.containsKey("xtrmetl.replica.enabled"));
    }

    @Test
    void modernConfigDataWinsOverLegacyConfigData(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("application.properties");
        Files.writeString(configFile, """
                mightyetl.cdc.autostart=false
                xtrmetl.cdc.autostart=true
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
            assertEquals("false", context.getEnvironment().getProperty("mightyetl.cdc.autostart"));
            assertEquals("false", context.getEnvironment().getProperty("xtrmetl.cdc.autostart"));
        }
    }

    @Test
    void emptyWhenNeitherPrefixSet() {
        MockEnvironment env = new MockEnvironment();
        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);
        assertTrue(aliases.isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    static class AliasOrderProbeConfiguration {
    }
}
