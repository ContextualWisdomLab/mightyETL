package com.xtrmetl.cdc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the deployable CDC configuration against silently enabling local-debug observability.
 */
class CdcObservabilityProfileContractTest {

    @Test
    void baseConfigurationDoesNotForceDebugLoggingOrFullTraceSampling() {
        Properties properties = load("application.yml");

        assertNull(properties.getProperty("management.tracing.sampling.probability"),
                "base CDC configuration must retain Spring Boot's bounded default sampling policy");
        assertNull(properties.getProperty("logging.level.com.xtrmetl"),
                "base CDC configuration must not force application DEBUG logging");
        assertNull(properties.getProperty("logging.level.org.springframework.web"),
                "base CDC configuration must not force Spring Web DEBUG logging");
    }

    @Test
    void localProfileExplicitlyRestoresDeveloperVerboseObservability() {
        ClassPathResource localProfile = new ClassPathResource("application-local.yml");
        assertTrue(localProfile.exists(),
                "local CDC profile must preserve the opt-in developer observability contract");

        Properties properties = load("application-local.yml");
        assertEquals("local", properties.getProperty("spring.config.activate.on-profile"));
        assertEquals("1.0", properties.getProperty("management.tracing.sampling.probability"));
        assertEquals("DEBUG", properties.getProperty("logging.level.com.xtrmetl"));
        assertEquals("DEBUG", properties.getProperty("logging.level.org.springframework.web"));
    }

    private static Properties load(String resourceName) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resourceName));
        Properties properties = yaml.getObject();
        if (properties == null) {
            throw new IllegalStateException("Could not load " + resourceName);
        }
        return properties;
    }
}
