package com.xtrmetl.etl.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the repository boundary that keeps developer-local environment files out of Git.
 *
 * <p>The local Compose guidance uses environment overrides for database and service credentials.
 * Those files must remain untracked by default, while a non-secret {@code .env.example} template
 * may remain reviewable in source control.
 */
class RepositorySecretFilePolicyTest {

    @Test
    void localEnvironmentFilesAreIgnoredWhileExampleTemplateMayBeTracked() throws IOException {
        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        assertNotNull(repositoryRoot, "repository root containing .gitignore must be discoverable");

        List<String> ignoredPatterns = Files.readAllLines(repositoryRoot.resolve(".gitignore"));

        assertTrue(ignoredPatterns.contains(".env"), "root .gitignore must ignore .env");
        assertTrue(ignoredPatterns.contains(".env.*"), "root .gitignore must ignore environment-specific .env files");
        assertTrue(ignoredPatterns.contains("!.env.example"), "root .gitignore must permit a reviewed non-secret .env.example template");
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
