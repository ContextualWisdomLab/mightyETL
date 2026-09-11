package com.xtrmetl.cdc.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the repository-wide Mockito 5 startup-agent contract against module-local mock-maker drift.
 *
 * <p>Mockito 5 defaults to the inline mock maker. A lingering {@code mock-maker-subclass} resource
 * silently overrides that default even when {@code mockito-core} is loaded at JVM startup, so every
 * test module must leave mock-maker selection to the maintained Mockito default.</p>
 */
final class MockitoMockMakerOverrideContractTest {

    private static final Path LEGACY_OVERRIDE_SUFFIX = Path.of(
            "src", "test", "resources", "mockito-extensions", "org.mockito.plugins.MockMaker");

    @Test
    void noTestModuleForcesTheLegacySubclassMockMaker() throws IOException {
        Path repositoryRoot = findProjectRoot();

        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            List<Path> overrides = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.endsWith(LEGACY_OVERRIDE_SUFFIX))
                    .map(repositoryRoot::relativize)
                    .sorted()
                    .toList();

            assertTrue(
                    overrides.isEmpty(),
                    "Mockito 5 startup instrumentation requires removing legacy overrides: " + overrides
            );
        }
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPom = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPom = current;
            }
            current = current.getParent();
        }
        if (lastPom != null) {
            return lastPom;
        }
        throw new IllegalStateException("project root not found");
    }
}
