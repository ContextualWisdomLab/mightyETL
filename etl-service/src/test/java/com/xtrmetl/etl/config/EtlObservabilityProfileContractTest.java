package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards deployable ETL configuration against silently enabling developer-verbose observability.
 *
 * <p>The default service profile is production-facing configuration authority and must not force
 * full trace sampling or DEBUG application/web logging. Developers may explicitly opt into the
 * historical verbose posture through the {@code local} Spring profile.</p>
 */
class EtlObservabilityProfileContractTest {

    /**
     * Verifies that deployable ETL defaults do not override production-safe observability posture.
     */
    @Test
    void baseConfigurationDoesNotForceDebugLoggingOrFullTraceSampling() {
        Properties properties = load("application.yml");

        assertNull(
                properties.getProperty("management.tracing.sampling.probability"),
                "base ETL configuration must retain Spring Boot's bounded default sampling policy"
        );
        assertNull(
                properties.getProperty("logging.level.com.xtrmetl"),
                "base ETL configuration must not force application DEBUG logging"
        );
        assertNull(
                properties.getProperty("logging.level.org.springframework.web"),
                "base ETL configuration must not force Spring Web DEBUG logging"
        );
    }

    /**
     * Verifies that developers retain an explicit local-only verbose observability profile.
     */
    @Test
    void localProfileExplicitlyRestoresDeveloperVerboseObservability() {
        ClassPathResource localProfile = new ClassPathResource("application-local.yml");
        assertTrue(
                localProfile.exists(),
                "local ETL profile must preserve the opt-in developer observability contract"
        );

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
