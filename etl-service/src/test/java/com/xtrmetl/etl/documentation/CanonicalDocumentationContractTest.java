package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the canonical product, technical, architecture, decision, UML, ERD, and operational
 * documentation needed to understand mightyETL without reconstructing pull-request bodies or chat
 * history.
 */
class CanonicalDocumentationContractTest {

    private static final Path PROJECT_ROOT = projectRoot();

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

    @Test
    void rootDocumentsDescribeCurrentDurableAndEvidenceBoundaries() throws IOException {
        String prd = read("PRD.md");
        String trd = read("TRD.md");
        String architecture = read("ARCHITECTURE.md");

        for (String token : List.of(
                "POST /api/etl/process",
                "Idempotency-Key",
                "POST /api/etl/jobs",
                "GET /api/etl/jobs/{job_record_id}",
                "implemented_on_develop",
                "active_pr",
                "known_gap"
        )) {
            assertTrue(prd.contains(token), "PRD misses current contract: " + token);
        }
        for (String token : List.of(
                "bounded atomic",
                "etl_idempotency_records",
                "etl_job_records",
                "exact-head",
                "synthetic-merge"
        )) {
            assertTrue(trd.contains(token), "TRD misses current contract: " + token);
        }
        for (String token : List.of(
                "EtlJobController",
                "etl_idempotency_records",
                "etl_job_records",
                "known_gap",
                "active_pr",
                "Kafka broker acknowledgement"
        )) {
            assertTrue(architecture.contains(token), "Architecture misses current contract: " + token);
        }
    }

    @Test
    void historicalAuthenticationAndParallelDesignsAreExplicitlySuperseded() throws IOException {
        String prd = read("PRD.md");
        String architecture = read("ARCHITECTURE.md");
        String traceability = read("docs/TRACEABILITY.md");

        assertTrue(prd.contains("superseded interface: `POST /auth/signin`"));
        assertTrue(prd.contains("superseded interface: `POST /auth/signup`"));
        assertTrue(architecture.contains("superseded interface: `POST /auth/signin`"));
        assertTrue(architecture.contains("superseded security claim: `BCrypt`"));
        assertTrue(architecture.contains("earlier per-record `CompletableFuture`/`Parallel Proc` architecture is retired"));
        assertTrue(traceability.contains("`superseded`: local `/auth/signup` and `/auth/signin`"));
        assertTrue(traceability.contains("`superseded`: per-record CompletableFuture fan-out"));
    }

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
        assertTrue(erd.contains("legacy persisted compatibility state"));

        for (String status : List.of(
                "implemented_on_develop",
                "active_pr",
                "planned",
                "superseded",
                "out_of_scope",
                "known_gap"
        )) {
            assertTrue(traceability.contains(status), "Traceability misses status taxonomy: " + status);
        }
    }

    @Test
    void capabilityStatusesAreBoundToSourceBackedClaims() throws IOException {
        String traceability = read("docs/TRACEABILITY.md");
        String apiContract = read("docs/API_CONTRACT.md");

        for (String row : List.of(
                "| bounded whole-batch ETL admission | `implemented_on_develop` |",
                "| principal-scoped Idempotency-Key | `implemented_on_develop` |",
                "| durable asynchronous intake/status | `implemented_on_develop` |",
                "| owner cancellation / CANCELLED | `active_pr` #147 |",
                "| production JWT Resource Server | `active_pr` #142 |",
                "| protected gateway current state | `known_gap` |",
                "| any-to-any canonical CDC | `planned` |"
        )) {
            assertTrue(traceability.contains(row), "Traceability misses capability/status binding: " + row);
        }

        assertTrue(apiContract.contains("`instance`, and `errorCode`"));
        assertTrue(apiContract.contains("ProblemDetail.setInstance(...)"));
        assertTrue(apiContract.contains("does not expose a separate public `path` alias"));
    }

    @Test
    void adrIndexCarriesCoreCrossCuttingDecisions() throws IOException {
        String index = read("docs/adr/README.md");
        for (String adr : List.of("0001", "0002", "0003", "0004", "0005", "0006", "0007", "0008")) {
            assertTrue(index.contains("[" + adr + "]"), "ADR index misses " + adr);
        }
        assertTrue(index.contains("Accepted"));
        assertTrue(index.contains("Known gaps") || index.contains("known gaps"));
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
