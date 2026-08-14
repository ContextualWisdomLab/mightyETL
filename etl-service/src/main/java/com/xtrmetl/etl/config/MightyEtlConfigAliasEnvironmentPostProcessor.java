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
 * Dual-read configuration bridge: {@code mightyetl.*} is preferred and mirrored to the legacy
 * {@code xtrmetl.*} namespace used by existing configuration-property consumers.
 *
 * <p>Only explicitly supported product keys are mirrored. See
 * {@code docs/rebrand-name-matrix.md} for compatibility boundaries.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MightyEtlConfigAliasEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** Name of the highest-precedence synthetic alias property source. */
    public static final String PROPERTY_SOURCE_NAME = "mightyetl-xtrmetl-aliases";

    static final List<String> RELATIVE_KEYS = List.of(
            "etl.max-payload-bytes",
            "etl.max-batch-records",
            "etl.jobs.intake-enabled",
            "etl.jobs.worker.enabled",
            "etl.jobs.worker.fixed-delay-milliseconds",
            "etl.jobs.worker.initial-delay-milliseconds",
            "etl.jobs.worker.lease-duration-seconds",
            "etl.jobs.worker.max-attempts",
            "etl.jobs.worker.lease-owner-id",
            "connectors.databricks.enabled",
            "connectors.snowflake.enabled",
            "connectors.qlik-sense.enabled"
    );

    /**
     * Adds known aliases ahead of ordinary configuration sources.
     *
     * @param environment configurable application environment
     * @param application Spring application being prepared
     */
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
