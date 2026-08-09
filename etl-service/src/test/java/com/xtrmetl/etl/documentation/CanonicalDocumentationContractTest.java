package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the canonical product, technical, architecture, decision, UML, ERD, and operational
 * documentation needed to understand mightyETL without reconstructing pull-request bodies or chat
 * history.
 */
class CanonicalDocumentationContractTest {

    private static final Path PROJECT_ROOT = projectRoot();

    /** Requires every acquisition-diligence documentation family to have a canonical entry point. */
    @Test
    void canonicalDocumentationFamiliesArePresent() {
        List<String> requiredPaths = List.of(
                "PRD.md",
                "TRD.md",
                "ARCHITECTURE.md",
                "SECURITY.md",
                "docs/adr/README.md",
                "docs/UML.md",
                "docs/ERD.md",
                "docs/API_CONTRACT.md",
                "docs/THREAT_MODEL.md",
                "docs/TEST_STRATEGY.md",
                "docs/OPERABILITY.md",
                "docs/TRACEABILITY.md",
                "docs/DOCUMENTATION_ASSESSMENT.md"
        );

        for (String requiredPath : requiredPaths) {
            assertTrue(
                    Files.isRegularFile(PROJECT_ROOT.resolve(requiredPath)),
                    "Canonical documentation entry point is missing: " + requiredPath
            );
        }
    }

    /** Requires root product and technical documents to describe the actual protected-develop API. */
    @Test
    void rootProductAndTechnicalDocumentsDescribeCurrentDurableBoundaries() throws IOException {
        String prd = read("PRD.md");
        String trd = read("TRD.md");
        String architecture = read("ARCHITECTURE.md");

        for (String currentContract : List.of(
                "POST /api/etl/process",
                "Idempotency-Key",
                "POST /api/etl/jobs",
                "GET /api/etl/jobs/{job_record_id}",
                "implemented_on_develop",
                "active_pr"
        )) {
            assertTrue(prd.contains(currentContract), "PRD misses current contract: " + currentContract);
        }

        for (String currentContract : List.of(
                "bounded atomic",
                "etl_idempotency_records",
                "etl_job_records",
                "exact-head",
                "synthetic-merge"
        )) {
            assertTrue(trd.contains(currentContract), "TRD misses current contract: " + currentContract);
        }

        for (String currentContract : List.of(
                "EtlJobController",
                "etl_idempotency_records",
                "etl_job_records",
                "known_gap",
                "active_pr"
        )) {
            assertTrue(
                    architecture.contains(currentContract),
                    "Architecture misses current contract: " + currentContract
            );
        }
    }

    /** Prevents historical authentication and parallel-batch claims from masquerading as shipped truth. */
    @Test
    void canonicalRootDocumentsRejectSupersededProductClaims() throws IOException {
        String prd = read("PRD.md");
        String trd = read("TRD.md");
        String architecture = read("ARCHITECTURE.md");

        assertFalse(prd.contains("POST /auth/signin"), "PRD must not advertise an unshipped sign-in API");
        assertFalse(prd.contains("POST /auth/signup"), "PRD must not advertise an unshipped sign-up API");
        assertFalse(prd.contains("CREATE TABLE users"), "PRD must not invent a users table");
        assertFalse(prd.contains("CREATE TABLE roles"), "PRD must not invent a roles table");
        assertFalse(
                trd.contains("resilient to partial failures"),
                "TRD must describe atomic batch rollback instead of partial commit semantics"
        );
        assertFalse(
                architecture.contains("BCrypt"),
                "Architecture must not describe an authentication implementation absent from develop"
        );
        assertFalse(
                architecture.contains("Parallel Proc"),
                "Architecture must not describe the retired per-record fan-out implementation"
        );
    }

    /** Requires diagrams and data-model docs to identify current versus future persisted state. */
    @Test
    void diagramsAndDataModelSeparateImplementedFromActivePullRequests() throws IOException {
        String uml = read("docs/UML.md");
        String erd = read("docs/ERD.md");
        String traceability = read("docs/TRACEABILITY.md");

        assertTrue(uml.contains("```mermaid"));
        assertTrue(uml.contains("stateDiagram-v2"));
        assertTrue(uml.contains("sequenceDiagram"));
        assertTrue(uml.contains("implemented_on_develop"));
        assertTrue(uml.contains("active_pr"));

        assertTrue(erd.contains("erDiagram"));
        assertTrue(erd.contains("etl_idempotency_records"));
        assertTrue(erd.contains("etl_job_records"));
        assertTrue(erd.contains("implemented_on_develop"));
        assertTrue(erd.contains("active_pr"));

        for (String status : List.of(
                "implemented_on_develop",
                "active_pr",
                "planned",
                "superseded",
                "out_of_scope"
        )) {
            assertTrue(traceability.contains(status), "Traceability misses status taxonomy: " + status);
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
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
