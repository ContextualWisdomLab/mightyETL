package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates canonical documentation structure and consistency against the current mightyETL product
 * rather than preserving historical reverse-engineering assumptions as shipped behavior.
 */
@DisplayName("Documentation Validation Tests")
class DocumentationValidationTest {

    private static final Path PROJECT_ROOT = projectRoot();
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");

    @Nested
    @DisplayName("README.md Tests")
    class ReadmeTests {
        @Test
        void readmeKeepsProductServicesAndQuickStartDiscoverable() throws IOException {
            String readme = read("README.md");
            assertTrue(readme.contains("# mightyETL"));
            assertTrue(readme.contains("Quick Start"));
            assertTrue(readme.contains("Architecture"));
            for (String service : List.of(
                    "CDC Service", "ETL Service", "Gateway", "Eureka", "Config Server"
            )) {
                assertTrue(readme.contains(service), "README misses service: " + service);
            }
        }

        @Test
        void readmeDoesNotAdvertiseThePlaceholderGatewayAsProductionJwt() throws IOException {
            String readme = read("README.md");
            assertTrue(readme.contains("does not currently provide production cryptographic JWT validation"));
            assertTrue(readme.contains("`/auth/signup` and `/auth/signin` examples are **superseded design notes"));
            assertTrue(readme.contains("active_pr #142"));
        }

        @Test
        void readmeInternalLinksResolve() throws IOException {
            assertInternalLinksResolve("README.md");
        }
    }

    @Nested
    @DisplayName("PRD.md Tests")
    class PrdTests {
        @Test
        void prdHasCurrentProductStructureAndRequirements() throws IOException {
            String prd = read("PRD.md");
            for (String section : List.of(
                    "Executive Summary", "Problem Statement", "Solution Overview",
                    "Functional Requirements", "Non-Functional Requirements",
                    "Data Model", "API Specifications", "Deployment Architecture",
                    "Success Metrics", "Risk Assessment"
            )) {
                assertTrue(prd.contains(section), "PRD misses section: " + section);
            }
            for (String idPrefix : List.of("FR-ETL-", "FR-CDC-", "FR-AUTH-", "FR-OPS-", "NFR-")) {
                assertTrue(prd.contains(idPrefix), "PRD misses requirement family: " + idPrefix);
            }
        }

        @Test
        void prdUsesProtectedRealityAndExplicitStatusTaxonomy() throws IOException {
            String prd = read("PRD.md");
            for (String token : List.of(
                    "POST /api/etl/process", "POST /api/etl/jobs",
                    "GET /api/etl/jobs/{job_record_id}", "Idempotency-Key",
                    "etl_idempotency_records", "etl_job_records",
                    "implemented_on_develop", "active_pr", "known_gap"
            )) {
                assertTrue(prd.contains(token), "PRD misses current token: " + token);
            }
            assertTrue(prd.contains("superseded interface: `POST /auth/signin`"));
            assertTrue(prd.contains("superseded interface: `POST /auth/signup`"));
            assertTrue(prd.contains("legacy compose bootstrap: CREATE TABLE roles"));
            assertTrue(prd.contains("legacy compose bootstrap: CREATE TABLE users"));
        }
    }

    @Nested
    @DisplayName("Architecture Tests")
    class ArchitectureTests {
        @Test
        void architectureContainsCurrentServicesFlowsSecurityAndDeployment() throws IOException {
            String architecture = read("ARCHITECTURE.md");
            assertTrue(architecture.contains("```mermaid"));
            assertTrue(architecture.contains("ETL Processing Flow"));
            assertTrue(architecture.contains("CDC Event Capture Flow"));
            assertTrue(architecture.contains("Security Architecture"));
            assertTrue(architecture.contains("Deployment Architecture"));
            for (String service : List.of(
                    "CDC Service", "ETL Service", "Gateway", "Eureka", "Config Server", "Zipkin"
            )) {
                assertTrue(architecture.contains(service), "Architecture misses: " + service);
            }
            for (String technology : List.of("Debezium", "Kafka", "PostgreSQL", "Spring")) {
                assertTrue(architecture.contains(technology), "Architecture misses: " + technology);
            }
        }

        @Test
        void architectureDoesNotHideKnownSecurityAndCdcGaps() throws IOException {
            String architecture = read("ARCHITECTURE.md");
            assertTrue(architecture.contains("literal example value `valid_token`"));
            assertTrue(architecture.contains("PR #142"));
            assertTrue(architecture.contains("PR #139"));
            assertTrue(architecture.contains("Issue #141"));
            assertTrue(architecture.contains("synthetic merge"));
        }
    }

    @Nested
    @DisplayName("Cross-document consistency")
    class CrossDocumentTests {
        @Test
        void servicePortsAreConsistentAcrossCanonicalDocs() throws IOException {
            Map<String, String> docs = Map.of(
                    "PRD", read("PRD.md"),
                    "TRD", read("TRD.md"),
                    "ARCH", read("ARCHITECTURE.md")
            );
            for (String port : List.of("8080", "8000", "8001", "8761", "8888", "9412")) {
                for (Map.Entry<String, String> entry : docs.entrySet()) {
                    assertTrue(entry.getValue().contains(port), entry.getKey() + " misses port " + port);
                }
            }
        }

        @Test
        void canonicalDocsShareCurrentImplementationVocabulary() throws IOException {
            List<String> docs = List.of(read("PRD.md"), read("TRD.md"), read("ARCHITECTURE.md"));
            for (String token : List.of(
                    "implemented_on_develop", "active_pr", "etl_idempotency_records", "etl_job_records"
            )) {
                for (String doc : docs) {
                    assertTrue(doc.contains(token), "Canonical document misses shared token: " + token);
                }
            }
        }

        @Test
        void capabilityStatusLabelsCannotBeSatisfiedByUnrelatedTokens() throws IOException {
            String traceability = read("docs/TRACEABILITY.md");
            String apiContract = read("docs/API_CONTRACT.md");
            String erd = read("docs/ERD.md");

            assertTrue(traceability.contains("| owner cancellation / CANCELLED | `active_pr` #147 |"));
            assertTrue(traceability.contains("| production JWT Resource Server | `active_pr` #142 |"));
            assertTrue(traceability.contains("| protected gateway current state | `known_gap` |"));
            assertTrue(traceability.contains("| any-to-any canonical CDC | `planned` |"));
            assertTrue(apiContract.contains("`instance`, and `errorCode`"));
            assertTrue(apiContract.contains("does not expose a separate public `path` alias"));
            assertTrue(erd.contains("`implemented_on_develop` schema, `known_gap` runtime retention"));
        }
    }

    @Nested
    @DisplayName("Content quality")
    class ContentQualityTests {
        @ParameterizedTest
        @ValueSource(strings = {
                "README.md", "SUMMARY_KR.md", "PRD.md", "TRD.md", "ARCHITECTURE.md", "SECURITY.md",
                "docs/UML.md", "docs/ERD.md", "docs/API_CONTRACT.md",
                "docs/THREAT_MODEL.md", "docs/TEST_STRATEGY.md", "docs/OPERABILITY.md",
                "docs/TRACEABILITY.md", "docs/DOCUMENTATION_ASSESSMENT.md"
        })
        void canonicalDocumentsUseOnlyIntentionalMarkdownLineBreakWhitespace(String filename) throws IOException {
            for (String line : Files.readAllLines(PROJECT_ROOT.resolve(filename), StandardCharsets.UTF_8)) {
                assertFalse(line.endsWith("\t"), filename + " has trailing tab whitespace");
                int trailingSpaces = trailingSpaces(line);
                assertTrue(
                        trailingSpaces == 0 || trailingSpaces == 2,
                        filename + " has non-canonical trailing spaces: " + trailingSpaces
                );
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "README.md", "SUMMARY_KR.md", "PRD.md", "TRD.md", "ARCHITECTURE.md", "SECURITY.md",
                "docs/UML.md", "docs/ERD.md", "docs/API_CONTRACT.md",
                "docs/THREAT_MODEL.md", "docs/TEST_STRATEGY.md", "docs/OPERABILITY.md",
                "docs/TRACEABILITY.md", "docs/DOCUMENTATION_ASSESSMENT.md",
                "docs/adr/README.md"
        })
        void canonicalInternalLinksResolve(String filename) throws IOException {
            assertInternalLinksResolve(filename);
        }
    }

    private static int trailingSpaces(String line) {
        int count = 0;
        for (int index = line.length() - 1; index >= 0 && line.charAt(index) == ' '; index--) {
            count++;
        }
        return count;
    }

    private static void assertInternalLinksResolve(String relativePath) throws IOException {
        String content = read(relativePath);
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(content);
        Path documentDirectory = PROJECT_ROOT.resolve(relativePath).getParent();
        if (documentDirectory == null) {
            documentDirectory = PROJECT_ROOT;
        }
        while (matcher.find()) {
            String link = matcher.group(2);
            if (link.startsWith("http://") || link.startsWith("https://") || link.startsWith("#")) {
                continue;
            }
            String pathOnly = link.split("#", 2)[0];
            if (pathOnly.isBlank()) {
                continue;
            }
            assertTrue(
                    Files.exists(documentDirectory.resolve(pathOnly).normalize()),
                    relativePath + " has unresolved internal link: " + link
            );
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

    /** Finds the repository root from repository-root or module-scoped Maven execution. */
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
