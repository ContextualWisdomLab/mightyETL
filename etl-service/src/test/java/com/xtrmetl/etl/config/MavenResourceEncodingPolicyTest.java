package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards Maven resource copying against platform-default character encodings.
 *
 * <p>The Java compiler encoding and Maven Resources Plugin encoding are separate build inputs.
 * Declaring {@code project.build.sourceEncoding} at the root project keeps main and test resource
 * copying deterministic across supported operating systems and inherited reactor modules.</p>
 */
class MavenResourceEncodingPolicyTest {

    @Test
    void rootPomDeclaresUtf8ProjectBuildSourceEncoding() throws IOException {
        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        assertNotNull(repositoryRoot, "repository root containing pom.xml must be discoverable");

        String rootPom = Files.readString(repositoryRoot.resolve("pom.xml"));
        assertTrue(
                rootPom.contains("<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>"),
                "root pom.xml must explicitly declare project.build.sourceEncoding=UTF-8 for Maven resources"
        );
    }

    private static Path findRepositoryRoot(Path start) {
        Path candidate = start;
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("etl-service"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }
}
