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
            "etl.max-batch-records",
            "etl.max-concurrency",
            "etl.queue-capacity",
            "connectors.databricks.enabled",
            "connectors.snowflake.enabled",
            "connectors.qlik-sense.enabled"
    );

    private static final Map<String, String> SHORT_ENVIRONMENT_ALIASES = Map.of(
            "ETL_MAX_BATCH_RECORDS", "etl.max-batch-records",
            "ETL_MAX_CONCURRENCY", "etl.max-concurrency",
            "ETL_QUEUE_CAPACITY", "etl.queue-capacity"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> aliases = buildAliases(environment);
        if (!aliases.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, aliases));
        }
    }

    /**
     * Builds aliases with the modern product prefix taking precedence over compatibility and
     * short-form environment names.
     *
     * @param environment current Spring environment
     * @return properties to prepend to the environment, or an empty map when no known keys exist
     */
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

        for (Map.Entry<String, String> entry : SHORT_ENVIRONMENT_ALIASES.entrySet()) {
            String modernKey = "mightyetl." + entry.getValue();
            String legacyKey = "xtrmetl." + entry.getValue();
            if (environment.getProperty(modernKey) != null
                    || environment.getProperty(legacyKey) != null) {
                continue;
            }

            String value = environment.getProperty(entry.getKey());
            if (value != null) {
                aliases.put(modernKey, value);
                aliases.put(legacyKey, value);
            }
        }
        return aliases;
    }
}
