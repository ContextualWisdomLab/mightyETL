package com.xtrmetl.cdc.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards secure CDC defaults and the legacy-to-semantic source configuration boundary.
 */
class XtrmetlPropertiesSecurityDefaultTest {

    @Test
    void replicaDdlRemainsDisabledAndUsesWhitelistValidationByDefault() {
        XtrmetlProperties xtrmetlProperties = new XtrmetlProperties();

        assertFalse(
                xtrmetlProperties.getReplica().isDdlEnabled(),
                "DDL replication must remain disabled by default"
        );
        assertEquals(
                "whitelist",
                xtrmetlProperties.getReplica().getDdlValidationMode(),
                "the Java configuration object must match the deployable and metadata secure default"
        );
    }

    @Test
    void defaultCdcSourceUsesSemanticJavaIdentifiers() {
        XtrmetlProperties xtrmetlProperties = new XtrmetlProperties();
        XtrmetlProperties.Source sourceConfiguration =
                xtrmetlProperties.getCdc().getSources().getFirst();

        assertEquals("pg-main", sourceConfiguration.getSourceId());
        assertEquals("postgres-debezium", sourceConfiguration.getSourceType());
    }

    @Test
    void legacySourceKeysBindIntoSemanticJavaIdentifiers() {
        MapConfigurationPropertySource configurationSource =
                new MapConfigurationPropertySource(Map.of(
                        "xtrmetl.cdc.sources[0].id", "pg-legacy",
                        "xtrmetl.cdc.sources[0].type", "mysql-debezium",
                        "xtrmetl.cdc.sources[0].enabled", "false"
                ));

        XtrmetlProperties xtrmetlProperties = new Binder(configurationSource)
                .bind("xtrmetl", Bindable.of(XtrmetlProperties.class))
                .orElseThrow();
        XtrmetlProperties.Source sourceConfiguration =
                xtrmetlProperties.getCdc().getSources().getFirst();

        assertEquals("pg-legacy", sourceConfiguration.getSourceId());
        assertEquals("mysql-debezium", sourceConfiguration.getSourceType());
        assertFalse(sourceConfiguration.isEnabled());
    }
}
