package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requires durable cross-cutting architecture decisions and diagrams discovered after the initial
 * canonical documentation spine. Missing files or semantics must fail as documentation defects.
 */
class CrossCuttingArchitectureAuthorityTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void schemaMigrationAndRecoveryAuthorityIsIndexed() throws IOException {
        assertAdr(
                "0009-schema-migration-and-recovery-authority.md",
                "**Status:** Accepted with known gaps",
                "Flyway is the sole production schema-mutation authority"
        );
    }

    @Test
    void serviceAndConfigurationIdentityAuthorityIsIndexed() throws IOException {
        assertAdr(
                "0010-service-and-configuration-identity-authority.md",
                "**Status:** Accepted with known gaps",
                "No trust boundary inherits another boundary's authentication"
        );
    }

    @Test
    void diagnosticAndDeadLetterDataGovernanceIsIndexed() throws IOException {
        assertAdr(
                "0011-diagnostic-and-dead-letter-data-governance.md",
                "**Status:** Accepted with known gaps",
                "Dead-letter records are terminal quarantine"
        );
    }

    @Test
    void qualitySecurityReviewAndReleaseEvidenceAuthorityIsIndexed() throws IOException {
        assertAdr(
                "0012-quality-security-review-and-release-evidence.md",
                "**Status:** Accepted with known gaps",
                "A green aggregate is not a release authority"
        );
    }

    @Test
    void runtimeIdentifierAndStatefulCompatibilityAuthorityIsIndexed() throws IOException {
        assertAdr(
                "0013-runtime-identifier-and-stateful-compatibility.md",
                "**Status:** Accepted with known gaps",
                "Runtime identifiers migrate by semantic category"
        );
    }

    @Test
    void tenancyAndDataLifecycleDecisionIsExplicitlyProposed() throws IOException {
        assertAdr(
                "0014-tenancy-and-data-lifecycle-authority.md",
                "**Status:** Proposed",
                "Principal scoping is not tenant isolation"
        );
    }

    @Test
    void architectureDefinesCurrentCrossCuttingAuthorityModel() throws IOException {
        String architecture = readDocument("ARCHITECTURE.md");

        assertTrue(architecture.contains("## 15. Cross-Cutting Authority Model"));
        assertTrue(architecture.contains("### 15.1 Service and configuration identity authority"));
        assertTrue(architecture.contains("### 15.2 Schema mutation and recovery authority"));
        assertTrue(architecture.contains("### 15.3 Diagnostic, dead-letter, and data-lifecycle authority"));
        assertTrue(architecture.contains("### 15.4 Evidence, review, and release authority"));
    }

    @Test
    void umlCoversIdentityRecoveryDeadLetterAndReleaseAuthority() throws IOException {
        String uml = readDocument("docs/UML.md");

        assertTrue(uml.contains("## 13. Service and configuration identity authority"));
        assertTrue(uml.contains("## 14. Schema and recovery authority"));
        assertTrue(uml.contains("## 15. Dead-letter lifecycle authority"));
        assertTrue(uml.contains("## 16. Evidence and release authority"));
    }

    @Test
    void erdSeparatesRelationalTruthFromExternalArtifacts() throws IOException {
        String erd = readDocument("docs/ERD.md");

        assertTrue(erd.contains("## 10. Logical external artifact model"));
        assertTrue(erd.contains("backup_bundle"));
        assertTrue(erd.contains("backup_manifest_record"));
        assertTrue(erd.contains("dead_letter_record"));
        assertTrue(erd.contains("external_effect_record"));
        assertTrue(erd.contains("service_identity"));
        assertTrue(erd.contains("tenant_scope"));
        assertTrue(erd.contains("conceptual or external unless a protected migration states otherwise"));
    }

    @Test
    void fitnessAndTraceabilityRecognizeCurrentCrossCuttingCoverage() throws IOException {
        String assessment = readDocument("docs/DOCUMENTATION_ASSESSMENT.md");
        String traceability = readDocument("docs/TRACEABILITY.md");

        assertTrue(assessment.contains("| Architecture | `present_current` on PR #149 |"));
        assertTrue(assessment.contains("| ADR | `present_current` on PR #149 |"));
        assertTrue(assessment.contains("| UML | `present_current` on PR #149 |"));
        assertTrue(assessment.contains("| ERD / logical data model | `present_current` on PR #149 |"));
        for (String adr : new String[]{"ADR-0009", "ADR-0010", "ADR-0011", "ADR-0012", "ADR-0013", "ADR-0014"}) {
            assertTrue(traceability.contains(adr), "Traceability must reference " + adr);
        }
    }

    private static void assertAdr(String fileName, String status, String invariant) throws IOException {
        Path adrPath = PROJECT_ROOT.resolve("docs/adr").resolve(fileName);
        assertTrue(Files.exists(adrPath), () -> "Missing canonical ADR: " + fileName);

        String index = readDocument("docs/adr/README.md");
        String adr = Files.readString(adrPath, StandardCharsets.UTF_8);
        assertTrue(index.contains("(" + fileName + ")"), () -> "ADR index must link " + fileName);
        assertTrue(adr.contains(status), () -> fileName + " must carry status " + status);
        assertTrue(adr.contains(invariant), () -> fileName + " must preserve invariant: " + invariant);
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
