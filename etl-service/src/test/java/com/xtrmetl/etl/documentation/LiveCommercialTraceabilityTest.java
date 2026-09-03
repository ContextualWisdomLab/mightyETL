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
 * traceability and documentation-fitness graph instead of living only in pull-request or issue bodies.
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
    }

    @Test
    void post169CommercialWorkIsBoundIntoTraceability() throws IOException {
        String traceability = readTraceability();

        assertTrue(traceability.contains("| diagnostic confidentiality hardening | `active_pr` #170/#171/#172/#174/#176/#211 |"));
        assertTrue(traceability.contains("| Flyway-only schema mutation authority | `active_pr` #184 |"));
        assertTrue(traceability.contains("| explicit Config Server repository authority | `active_pr` #189 |"));
        assertTrue(traceability.contains("| runtime identifier compatibility inventory | `active_pr` #191 |"));
        assertTrue(traceability.contains("| dead-letter privacy and terminal routing | `active_pr` #192/#197 |"));
        assertTrue(traceability.contains("| invalid amount fail-closed integrity | `active_pr` #199 |"));
        assertTrue(traceability.contains("| CDC connector registry identity | `active_pr` #201 |"));
        assertTrue(traceability.contains("| PostgreSQL backup and restore provenance | `active_pr` #208 |"));
        assertTrue(traceability.contains("| repository-wide owned-production coverage | `known_gap` issue #205 |"));
        assertTrue(traceability.contains("| Maven scanner dependency-graph completeness | `known_gap` issue #196 |"));
        assertTrue(traceability.contains("| structured record snapshot integrity | `active_pr` #222/#228 |"));
        assertTrue(traceability.contains("| public bootstrap and environment API documentation | `active_pr` #224/#226/#230 |"));
    }

    @Test
    void documentationFitnessAssessmentIncludesCurrentCrossCuttingWork() throws IOException {
        String assessment = readAssessment();

        assertTrue(assessment.contains("PR #160"));
        assertTrue(assessment.contains("issue #161"));
        assertTrue(assessment.contains("issue #162"));
        assertTrue(assessment.contains("PR #164"));
        assertTrue(assessment.contains("PR #163"));
        assertTrue(assessment.contains("issue #165"));
        assertTrue(assessment.contains("issue #166"));
        assertTrue(assessment.contains("PR #167"));
        assertTrue(assessment.contains("issue #168"));
        assertTrue(assessment.contains("PR #169"));
    }

    @Test
    void post169WorkIsVisibleInDocumentationFitnessAssessment() throws IOException {
        String assessment = readAssessment();

        assertTrue(assessment.contains("PR #184"));
        assertTrue(assessment.contains("PR #189"));
        assertTrue(assessment.contains("PR #191"));
        assertTrue(assessment.contains("PR #192"));
        assertTrue(assessment.contains("PR #197"));
        assertTrue(assessment.contains("PR #199"));
        assertTrue(assessment.contains("PR #201"));
        assertTrue(assessment.contains("PR #208"));
        assertTrue(assessment.contains("issue #196"));
        assertTrue(assessment.contains("issue #205"));
        assertTrue(assessment.contains("PR #222"));
        assertTrue(assessment.contains("PR #228"));
        assertTrue(assessment.contains("PR #230"));
    }

    @Test
    void testStrategyRejectsVacuousCoverageEvidence() throws IOException {
        String testStrategy = readTestStrategy();

        assertTrue(
                testStrategy.contains("selected production class set MUST be non-empty"),
                "Coverage policy must fail closed before applying percentage or zero-missed thresholds"
        );
        assertTrue(testStrategy.contains("Analyzed bundle with 0 classes"));
        assertTrue(testStrategy.contains("issue #162"));
        assertTrue(testStrategy.contains("PR #164"));
        assertTrue(testStrategy.contains("synthetic merge"));
        assertTrue(testStrategy.contains("literal source"));
    }

    private static String readTraceability() throws IOException {
        return readDocument("docs/TRACEABILITY.md");
    }

    private static String readAssessment() throws IOException {
        return readDocument("docs/DOCUMENTATION_ASSESSMENT.md");
    }

    private static String readTestStrategy() throws IOException {
        return readDocument("docs/TEST_STRATEGY.md");
    }

    private static String readDocument(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
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
