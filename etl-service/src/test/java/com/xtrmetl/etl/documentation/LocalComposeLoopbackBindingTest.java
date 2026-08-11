package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the bundled local Docker Compose profile does not publish ports on all host
 * interfaces by default.
 *
 * <p>Compose service-to-service traffic uses the internal network and service names. Published
 * host ports are therefore a developer-access boundary and must be explicitly loopback-bound in
 * the default local profile. This test does not claim that loopback binding is a production
 * authentication or transport-security control.</p>
 */
class LocalComposeLoopbackBindingTest {

    private static final Path PROJECT_ROOT = findProjectRoot();

    @Test
    void everyPublishedPortIsExplicitlyLoopbackBound() throws IOException {
        List<String> publishedPorts = Files.readAllLines(
                        PROJECT_ROOT.resolve("docker-compose.yml"),
                        StandardCharsets.UTF_8
                ).stream()
                .map(String::trim)
                .filter(LocalComposeLoopbackBindingTest::isPublishedPortMapping)
                .toList();

        assertFalse(
                publishedPorts.isEmpty(),
                "The local Compose contract must exercise at least one published host port"
        );

        List<String> nonLoopbackBindings = publishedPorts.stream()
                .filter(mapping -> !mapping.startsWith("- \"127.0.0.1:"))
                .toList();

        assertTrue(
                nonLoopbackBindings.isEmpty(),
                () -> "Default local Compose ports must bind to 127.0.0.1; unsafe mappings: "
                        + nonLoopbackBindings
        );
    }

    private static boolean isPublishedPortMapping(String line) {
        return line.matches("- \\\"(?:127\\.0\\.0\\.1:)?[0-9]+:[0-9]+\\\"");
    }

    private static Path findProjectRoot() {
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
        throw new IllegalStateException("Could not find project root (no .git or pom.xml found)");
    }
}
