package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures that newly opened material commercial-readiness work is reconciled into the canonical
 * traceability graph instead of living only in pull-request bodies.
 */
class LiveCommercialTraceabilityTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void sharedJacksonSecurityRepairIsTrackedAsUnshippedLiveWork() throws IOException {
        String traceability = Files.readString(
                PROJECT_ROOT.resolve("docs/TRACEABILITY.md"),
                StandardCharsets.UTF_8
        );

        assertTrue(
                traceability.contains("| shared Jackson security baseline | `active_pr` #160 |"),
                "New shared dependency-security work must be represented as active_pr, not omitted or marked shipped"
        );
        assertTrue(traceability.contains("Jackson 2.21.5"));
        assertTrue(traceability.contains("CVE-2026-54515"));
        assertTrue(traceability.contains("CVE-2026-59889"));
        assertTrue(traceability.contains("GHSA-mhm7-754m-9p8w"));
    }

    /** Finds the repository root from root- or module-scoped Maven execution. */
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
