package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards retirement of the abandoned local-auth bootstrap from the default PostgreSQL schema while
 * retaining an explicit opt-in compatibility artifact for installations that still require it.
 */
class LegacyAuthBootstrapRetirementTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void defaultBootstrapDoesNotCreateAbandonedLocalAuthObjects() throws IOException {
        String bootstrap = read("docker/postgres/init/01_schema.sql");

        assertFalse(bootstrap.contains("CREATE TABLE IF NOT EXISTS roles"));
        assertFalse(bootstrap.contains("CREATE TABLE IF NOT EXISTS users"));
        assertFalse(bootstrap.contains("CREATE TABLE IF NOT EXISTS user_roles"));
        assertFalse(bootstrap.contains("INSERT INTO roles"));
        assertTrue(bootstrap.contains("CREATE TABLE IF NOT EXISTS processed_data"));
    }

    @Test
    void legacyCompatibilityIsExplicitAndNeverRunsFromDefaultInitDirectory() throws IOException {
        String compatibility = read("docker/postgres/compat/legacy_auth_tables.sql");
        String compose = read("docker-compose.yml");

        assertTrue(compatibility.contains("DEPRECATED COMPATIBILITY"));
        assertTrue(compatibility.contains("CREATE TABLE IF NOT EXISTS roles"));
        assertTrue(compatibility.contains("CREATE TABLE IF NOT EXISTS users"));
        assertTrue(compatibility.contains("CREATE TABLE IF NOT EXISTS user_roles"));
        assertTrue(compatibility.contains("INSERT INTO roles"));

        assertTrue(compose.contains("./docker/postgres/init:/docker-entrypoint-initdb.d:ro"));
        assertFalse(compose.contains("./docker/postgres/compat:/docker-entrypoint-initdb.d"));
        assertFalse(compose.contains("legacy_auth_tables.sql:/docker-entrypoint-initdb.d"));

        assertFalse(
                PROJECT_ROOT.resolve("docker/postgres/compat").normalize()
                        .startsWith(PROJECT_ROOT.resolve("docker/postgres/init").normalize())
        );
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

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
