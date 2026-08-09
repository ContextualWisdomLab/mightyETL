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
 * traceability graph instead of living only in pull-request or issue bodies.
 */
class LiveCommercialTraceabilityTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void sharedJacksonSecurityRepairIsTrackedAsUnshippedLiveWork() throws IOException {
        String traceability = readTraceability();

        assertTrue(
                traceability.contains("| shared Jackson security baseline | `active_pr` #160 |"),
                "New shared dependency-security work must be represented as active_pr, not omitted or marked shipped"
        );
        assertTrue(traceability.contains("Jackson 2.21.5"));
        assertTrue(traceability.contains("CVE-2026-54515"));
        assertTrue(traceability.contains("CVE-2026-59889"));
        assertTrue(traceability.contains("GHSA-mhm7-754m-9p8w"));
    }

    @Test
    void vacuousCoverageGateIsTrackedAsAProtectedKnownGap() throws IOException {
        String traceability = readTraceability();

        assertTrue(
                traceability.contains("| non-vacuous durable-job coverage gate | `known_gap` issue #162 |"),
                "A protected quality gate that analyzes zero production classes must be visible as a known gap"
        );
        assertTrue(traceability.contains("Analyzed bundle with 0 classes"));
        assertTrue(traceability.contains("JaCoCo"));
        assertTrue(traceability.contains("class-file"));
    }

    @Test
    void currentCommercialWorkIsBoundIntoTraceability() throws IOException {
        String traceability = readTraceability();

        assertTrue(traceability.contains("| direct ETL service authentication | `known_gap` issue #161 |"));
        assertTrue(traceability.contains("| non-vacuous coverage repair | `active_pr` #164 |"));
        assertTrue(traceability.contains("| SQL Server CDC scaffold retirement | `active_pr` #163 |"));
        assertTrue(traceability.contains("| release artifact provenance | `planned` issue #165 |"));
        assertTrue(traceability.contains("| bundled Zipkin transport repair | `active_pr` #167 |"));
        assertTrue(traceability.contains("| repository runtime supply-chain cleanup | `active_pr` #169 |"));
        assertTrue(traceability.contains("| secure Maven Central transport | `planned` issue #170 |"));
        assertTrue(traceability.contains("| deterministic Maven plugin versions | `planned` issue #171 |"));
    }

    private static String readTraceability() throws IOException {
        return Files.readString(
                PROJECT_ROOT.resolve("docs/TRACEABILITY.md"),
                StandardCharsets.UTF_8
        );
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
