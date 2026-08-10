package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps target-connector diagnostic-confidentiality guidance tied to current security references.
 */
class TargetConnectorDiagnosticConfidentialityDocumentationTest {

    @Test
    void doctoringDocumentsProviderExceptionMinimizationAndCausalErrorRetention() throws IOException {
        Path doctoring = findProjectRoot().resolve(
                "docs/doctoring/target-connector-diagnostic-confidentiality.md"
        );
        assertTrue(Files.isRegularFile(doctoring),
                "target connector diagnostic-confidentiality doctoring must be checked in");

        String body = Files.readString(doctoring, StandardCharsets.UTF_8);
        assertTrue(body.contains("CWE-532"), "doctoring must map log exposure to CWE-532");
        assertTrue(body.contains("OWASP Logging Cheat Sheet"),
                "doctoring must cite current OWASP logging guidance");
        assertTrue(body.contains("suppressed exception"),
                "doctoring must preserve in-process suppressed-exception causality");
        assertTrue(body.contains("connector ID"),
                "doctoring must preserve bounded connector lifecycle classification");
        assertTrue(body.contains("connection string") || body.contains("connection strings"),
                "doctoring must identify provider connection strings as sensitive diagnostics");
        assertTrue(body.contains("purpose-bound") || body.contains("purpose limitation"),
                "doctoring must distinguish minimization from blanket masking");
        assertTrue(body.contains("APA 7"), "doctoring must identify the reference style");
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
