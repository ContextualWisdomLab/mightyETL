package com.xtrmetl.etl.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dual-read config bridge: {@code mightyetl.*} preferred, mirrored to {@code xtrmetl.*}.
 * See docs/rebrand-name-matrix.md.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MightyEtlConfigAliasEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "mightyetl-xtrmetl-aliases";

    static final List<String> RELATIVE_KEYS = List.of(
            "connectors.databricks.enabled",
            "connectors.snowflake.enabled",
            "connectors.qlik-sense.enabled"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> aliases = buildAliases(environment);
        if (!aliases.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, aliases));
        }
    }

    static Map<String, Object> buildAliases(org.springframework.core.env.Environment environment) {
        Map<String, Object> aliases = new LinkedHashMap<>();
        for (String relative : RELATIVE_KEYS) {
            String modernKey = "mightyetl." + relative;
            String legacyKey = "xtrmetl." + relative;
            String modernVal = environment.getProperty(modernKey);
            String legacyVal = environment.getProperty(legacyKey);

            if (modernVal != null) {
                aliases.put(legacyKey, modernVal);
            } else if (legacyVal != null) {
                aliases.put(modernKey, legacyVal);
            }
        }
        return aliases;
    }
}
