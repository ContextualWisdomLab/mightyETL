package com.xtrmetl.cdc.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps CDC diagnostic-confidentiality guidance and authoritative security references discoverable.
 */
class CdcDiagnosticConfidentialityDocumentationTest {

    @Test
    void doctoringDocumentsPurposeBoundDiagnosticMinimizationAndPrimarySecurityReferences() throws IOException {
        Path doctoring = findProjectRoot().resolve("docs/doctoring/cdc-diagnostic-confidentiality.md");
        assertTrue(Files.isRegularFile(doctoring),
                "cdc diagnostic-confidentiality doctoring must be checked in");

        String body = Files.readString(doctoring, StandardCharsets.UTF_8);
        assertTrue(body.contains("CWE-532"), "doctoring must map the weakness to CWE-532");
        assertTrue(body.contains("OWASP Logging Cheat Sheet"),
                "doctoring must cite current OWASP logging guidance");
        assertTrue(body.contains("row identifiers"),
                "doctoring must explain why business row identifiers do not belong in ordinary logs");
        assertTrue(body.contains("parser") && body.contains("exception"),
                "doctoring must cover parser exception diagnostics");
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
