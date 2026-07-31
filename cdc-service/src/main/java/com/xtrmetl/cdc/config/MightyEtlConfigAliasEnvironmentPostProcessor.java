package com.xtrmetl.cdc.config;

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
 * Dual-read config bridge for the mightyETL rebrand.
 *
 * <ul>
 *   <li>If {@code mightyetl.*} is set, it wins and is mirrored onto {@code xtrmetl.*}
 *       so existing {@code @ConfigurationProperties(prefix = "xtrmetl")} and
 *       {@code @Value("${xtrmetl...}")} keep working.</li>
 *   <li>If only {@code xtrmetl.*} is set, it is mirrored onto {@code mightyetl.*}
 *       for operators using the new prefix in tooling.</li>
 * </ul>
 *
 * <p>Not a full recursive alias of every key — only known product properties.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MightyEtlConfigAliasEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "mightyetl-xtrmetl-aliases";

    static final List<String> RELATIVE_KEYS = List.of(
            "cdc.autostart",
            "cdc.source-type",
            "cdc.canonical-map-enabled",
            "replica.enabled",
            "replica.group-id",
            "replica.topic-pattern",
            "replica.ddl-enabled",
            "replica.ddl-validation-mode",
            "replica.ddl-allowed-prefixes",
            "replica.ddl-blocked-prefixes",
            "replica.tables",
            "replica.kafka.concurrency",
            "replica.kafka.retry-backoff-ms",
            "replica.kafka.retry-max-attempts",
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

    /**
     * Visible for unit tests.
     */
    static Map<String, Object> buildAliases(org.springframework.core.env.Environment environment) {
        Map<String, Object> aliases = new LinkedHashMap<>();
        for (String relative : RELATIVE_KEYS) {
            String modernKey = "mightyetl." + relative;
            String legacyKey = "xtrmetl." + relative;
            String modernVal = environment.getProperty(modernKey);
            String legacyVal = environment.getProperty(legacyKey);

            if (modernVal != null) {
                // Modern name wins for legacy consumers.
                aliases.put(legacyKey, modernVal);
            } else if (legacyVal != null) {
                // Expose legacy values under the modern prefix for new docs/tools.
                aliases.put(modernKey, legacyVal);
            }
        }
        return aliases;
    }
}
