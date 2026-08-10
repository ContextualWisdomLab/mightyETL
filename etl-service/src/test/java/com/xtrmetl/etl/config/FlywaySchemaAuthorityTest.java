package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps production database schema mutation under one reviewed Flyway authority.
 */
class FlywaySchemaAuthorityTest {

    @Test
    void deployableConfigurationMakesFlywayTheOnlySchemaMutationAuthority() throws IOException {
        String application = read("etl-service/src/main/resources/application.yml");

        assertTrue(application.contains("flyway:"), "Deployable ETL configuration must keep Flyway enabled");
        assertTrue(application.contains("clean-disabled: true"), "Flyway clean must stay disabled");
        assertTrue(application.contains("baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}"),
                "Baseline-on-migrate must stay fail-closed by default");
        assertTrue(application.contains("ddl-auto: none"),
                "Hibernate must not mutate the production schema alongside Flyway");
        assertFalse(application.contains("ddl-auto: update"));
        assertFalse(application.contains("ddl-auto: create\n"));
        assertFalse(application.contains("ddl-auto: create-drop"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * Finds the reactor root from either root or module-local Maven execution.
     */
    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPomParent = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPomParent = current;
            }
            current = current.getParent();
        }
        if (lastPomParent != null) {
            return lastPomParent;
        }
        throw new IllegalStateException("Could not find project root");
    }
}
