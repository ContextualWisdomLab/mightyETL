package com.xtrmetl.cdc.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards beginner-readable documentation for the public CDC service lifecycle and construction
 * surface that operators and embedded-module consumers call directly.
 */
class CdcServiceDocumentationContractTest {

    private static final Path CDC_SERVICE_SOURCE = projectRoot().resolve(
            "cdc-service/src/main/java/com/xtrmetl/cdc/service/CdcService.java"
    );

    /**
     * Requires every currently exposed construction and lifecycle entry point to explain its
     * behavior instead of relying on method names or framework annotations alone.
     *
     * @throws IOException when the production source cannot be read
     */
    @Test
    void documentsPublicConstructionAndLifecycleSurface() throws IOException {
        String source = Files.readString(CDC_SERVICE_SOURCE, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(source.contains(
                "Constructs the CDC service with explicit startup, canonical-mapping, and change-mapping dependencies."
        ));
        assertTrue(source.contains("Returns whether automatic CDC startup is enabled."));
        assertTrue(source.contains("Handles Spring Boot application readiness by applying the configured CDC auto-start policy."));
        assertTrue(source.contains("Releases CDC engine and executor resources when the Spring bean is destroyed."));
    }

    /**
     * Locates the repository root for both reactor-root and module-local Maven execution.
     *
     * @return absolute repository root containing the root Maven project
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
