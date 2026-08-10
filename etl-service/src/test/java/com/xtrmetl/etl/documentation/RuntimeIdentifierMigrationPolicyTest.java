package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prevents the legacy xtrmETL identity from spreading beyond explicitly inventoried runtime surfaces.
 */
class RuntimeIdentifierMigrationPolicyTest {

    private static final String INVENTORY_PATH = "docs/compatibility/runtime-identifier-inventory.properties";

    @Test
    void legacyRuntimeIdentifiersAreExplicitlyInventoriedAndContainmentIsFailClosed() throws IOException {
        Path root = projectRoot();
        Path inventoryPath = root.resolve(INVENTORY_PATH);
        assertTrue(Files.exists(inventoryPath), "Runtime identifier migration requires a machine-readable inventory");

        Properties inventory = new Properties();
        try (Reader reader = Files.newBufferedReader(inventoryPath, StandardCharsets.UTF_8)) {
            inventory.load(reader);
        }

        assertEquals("mightyETL", inventory.getProperty("product.name"));
        assertEquals("com.xtrmetl", inventory.getProperty("legacy.maven.group"));
        assertEquals("xtrmETL", inventory.getProperty("legacy.maven.artifact"));
        assertEquals("com.xtrmetl", inventory.getProperty("legacy.java.package"));
        assertEquals("mightyetl.", inventory.getProperty("modern.config.prefix"));
        assertEquals("xtrmetl.", inventory.getProperty("legacy.config.prefix"));
        assertEquals("xtrmetl-cdc", inventory.getProperty("legacy.kafka.prefix"));
        assertEquals("planned", inventory.getProperty("migration.status"));
        assertEquals("major_version_staged", inventory.getProperty("migration.strategy"));

        List<String> allowedPrefixes = requiredCsv(inventory, "allowed.runtime.path.prefixes");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isRuntimeAuthoritySurface(root, path))
                    .forEach(path -> inspectLegacyMarker(root, path, allowedPrefixes, violations));
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Legacy runtime identity appeared outside the reviewed compatibility inventory: " + violations
        );
    }

    @Test
    void migrationInventoryExplainsCompatibilityAndPrimaryEvidence() throws IOException {
        Path inventoryPath = projectRoot().resolve(INVENTORY_PATH);
        assertTrue(Files.exists(inventoryPath), "Runtime identifier migration requires compatibility evidence");

        String inventory = Files.readString(inventoryPath, StandardCharsets.UTF_8);
        assertTrue(inventory.contains("reference.maven.relocation=https://maven.apache.org/guides/mini/guide-relocation.html"));
        assertTrue(inventory.contains("reference.java.binary_compatibility=https://docs.oracle.com/en/java/javase/26/docs/specs/jls/jls-13.html"));
        assertTrue(inventory.contains("compatibility.note="));
        assertFalse(inventory.contains("migration.status=implemented_on_develop"));
    }

    private static List<String> requiredCsv(Properties inventory, String key) {
        String raw = inventory.getProperty(key);
        assertTrue(raw != null && !raw.isBlank(), () -> "Missing non-empty inventory key: " + key);
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static boolean isRuntimeAuthoritySurface(Path root, Path path) {
        String relative = normalize(root.relativize(path));
        if (relative.equals("pom.xml") || relative.endsWith("/pom.xml")) {
            return true;
        }
        if (relative.equals("docker-compose.yml") || relative.equals(".replit")) {
            return true;
        }
        if (relative.startsWith("docker/")) {
            return isTextAuthorityFile(relative);
        }
        return relative.contains("/src/main/") && isTextAuthorityFile(relative);
    }

    private static boolean isTextAuthorityFile(String relative) {
        return relative.endsWith(".java")
                || relative.endsWith(".yml")
                || relative.endsWith(".yaml")
                || relative.endsWith(".properties")
                || relative.endsWith(".xml")
                || relative.endsWith(".sql");
    }

    private static void inspectLegacyMarker(
            Path root,
            Path path,
            List<String> allowedPrefixes,
            List<String> violations
    ) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.toLowerCase().contains("xtrmetl")) {
                return;
            }
            String relative = normalize(root.relativize(path));
            boolean allowed = allowedPrefixes.stream().anyMatch(prefix -> pathMatches(relative, prefix));
            if (!allowed) {
                violations.add(relative);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect runtime authority file: " + path, exception);
        }
    }

    private static boolean pathMatches(String relative, String prefix) {
        if (prefix.endsWith("/")) {
            return relative.startsWith(prefix);
        }
        if (prefix.startsWith("*/")) {
            return relative.endsWith(prefix.substring(1));
        }
        return relative.equals(prefix);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
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
