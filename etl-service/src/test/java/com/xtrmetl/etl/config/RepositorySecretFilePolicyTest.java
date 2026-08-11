package com.xtrmetl.etl.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards repository boundaries that keep developer-local environment files out of Git and Docker
 * build contexts.
 *
 * <p>The local Compose guidance uses environment overrides for database and service credentials.
 * Those files must remain untracked and excluded from Docker build transfer by default, while a
 * reviewed non-secret {@code .env.example} template may remain available for source-controlled
 * documentation.</p>
 */
class RepositorySecretFilePolicyTest {

    @Test
    void localEnvironmentFilesAreIgnoredWhileExampleTemplateMayBeTracked() throws IOException {
        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        assertNotNull(repositoryRoot, "repository root containing .gitignore must be discoverable");

        assertEnvironmentFileRules(
                Files.readAllLines(repositoryRoot.resolve(".gitignore")),
                "root .gitignore"
        );
    }

    @Test
    void localEnvironmentFilesAreExcludedFromDockerBuildContext() throws IOException {
        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        assertNotNull(repositoryRoot, "repository root containing .gitignore must be discoverable");

        Path dockerIgnore = repositoryRoot.resolve(".dockerignore");
        assertTrue(Files.isRegularFile(dockerIgnore), "root .dockerignore must exist");
        assertEnvironmentFileRules(Files.readAllLines(dockerIgnore), "root .dockerignore");
    }

    private static void assertEnvironmentFileRules(List<String> patterns, String boundary) {
        assertTrue(patterns.contains(".env"), boundary + " must ignore .env");
        assertTrue(patterns.contains(".env.*"), boundary + " must ignore environment-specific .env files");
        assertTrue(
                patterns.contains("!.env.example"),
                boundary + " must permit a reviewed non-secret .env.example template"
        );
    }

    private static Path findRepositoryRoot(Path start) {
        Path candidate = start;
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve(".gitignore"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }
}
