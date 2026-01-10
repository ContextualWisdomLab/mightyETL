package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive validation tests for project documentation files.
 * Tests ensure documentation quality, consistency, and accuracy.
 */
@DisplayName("Documentation Validation Tests")
class DocumentationValidationTest {

    // Minimum thresholds for requirements validation
    private static final int MIN_FUNCTIONAL_REQUIREMENTS = 10;
    private static final int MIN_NONFUNCTIONAL_REQUIREMENTS = 8;

    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern MARKDOWN_HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```([a-zA-Z]*)\n(.*?)\n```", Pattern.DOTALL);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+");

    /**
     * Locates the project root by walking up the directory tree until finding a marker file.
     */
    private static Path findProjectRoot() {
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
        throw new IllegalStateException("Could not find project root (no .git or pom.xml found)");
    }

    private static String readUtf8File(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("README.md Tests")
    class ReadmeTests {

        private String readmeContent;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws IOException {
            Path readmePath = PROJECT_ROOT.resolve("README.md");
            assertTrue(Files.exists(readmePath), "README.md should exist");
            readmeContent = readUtf8File(readmePath);
        }

        @Test
        @DisplayName("README should have proper document structure")
        void shouldHaveProperDocumentStructure() {
            assertNotNull(readmeContent);
            assertFalse(readmeContent.trim().isEmpty(), "README should not be empty");
            
            // Check for essential sections
            assertTrue(readmeContent.contains("# xtrmETL"), "Should have main title");
            assertTrue(readmeContent.contains("## Quick Start") || readmeContent.contains("## 🚀 Quick Start"),
                "Should have Quick Start section");
            assertTrue(readmeContent.contains("## Architecture") || readmeContent.contains("## 🏗️ Architecture"),
                "Should have Architecture section");
            assertTrue(readmeContent.contains("## Services") || readmeContent.contains("## 📚 Services"),
                "Should have Services section");
        }

        @Test
        @DisplayName("README should contain all service descriptions")
        void shouldContainAllServiceDescriptions() {
            String[] expectedServices = {
                "CDC Service", "ETL Service", "Zuul Gateway", 
                "Eureka Server", "Config Server"
            };
            
            for (String service : expectedServices) {
                assertTrue(readmeContent.contains(service), 
                    "README should contain description for " + service);
            }
        }

        @Test
        @DisplayName("README should have valid code blocks")
        void shouldHaveValidCodeBlocks() {
            Matcher matcher = CODE_BLOCK_PATTERN.matcher(readmeContent);
            int codeBlockCount = 0;
            
            while (matcher.find()) {
                codeBlockCount++;
                String language = matcher.group(1);
                String code = matcher.group(2);
                
                assertNotNull(code, "Code block should have content");
                assertFalse(code.trim().isEmpty(), "Code block should not be empty");
                
                // Validate specific language blocks
                if ("sql".equalsIgnoreCase(language)) {
                    assertTrue(code.toUpperCase().contains("CREATE") || 
                              code.toUpperCase().contains("INSERT") ||
                              code.toUpperCase().contains("SELECT"),
                        "SQL code block should contain valid SQL keywords");
                } else if ("bash".equalsIgnoreCase(language)) {
                    // Basic bash validation
                    assertFalse(code.contains("rm -rf /"), 
                        "Bash code should not contain dangerous commands");
                }
            }
            
            assertTrue(codeBlockCount > 0, "README should contain code examples");
        }

        @Test
        @DisplayName("README should reference correct ports")
        void shouldReferenceCorrectPorts() {
            Map<String, String> expectedPorts = new HashMap<>();
            expectedPorts.put("8080", "Zuul Gateway");
            expectedPorts.put("8000", "ETL Service");
            expectedPorts.put("8001", "CDC Service");
            expectedPorts.put("8761", "Eureka Server");
            expectedPorts.put("9412", "Zipkin");
            
            for (Map.Entry<String, String> entry : expectedPorts.entrySet()) {
                assertTrue(readmeContent.contains(entry.getKey()),
                    "README should mention port " + entry.getKey() + " for " + entry.getValue());
            }
        }

        @Test
        @DisplayName("README should have valid internal links")
        void shouldHaveValidInternalLinks() {
            Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(readmeContent);
            List<String> internalLinks = new ArrayList<>();
            
            while (matcher.find()) {
                String link = matcher.group(2);
                if (!link.startsWith("http") && !link.startsWith("#")) {
                    internalLinks.add(link);
                }
            }
            
            for (String link : internalLinks) {
                Path linkedFile = PROJECT_ROOT.resolve(link);
                assertTrue(Files.exists(linkedFile) || link.startsWith("#"),
                    "Internal link should point to existing file: " + link);
            }
        }

        @Test
        @DisplayName("README should mention authentication")
        void shouldMentionAuthentication() {
            assertTrue(readmeContent.toLowerCase().contains("jwt") || 
                      readmeContent.toLowerCase().contains("authentication"),
                "README should mention JWT or authentication");
            assertTrue(readmeContent.contains("/auth/signin") || 
                      readmeContent.contains("/auth/signup"),
                "README should mention authentication endpoints");
        }
    }

    @Nested
    @DisplayName("PRD.md Tests")
    class PrdTests {

        private String prdContent;
        private List<String> headers;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws IOException {
            Path prdPath = PROJECT_ROOT.resolve("PRD.md");
            assertTrue(Files.exists(prdPath), "PRD.md should exist");
            prdContent = readUtf8File(prdPath);
            headers = extractHeaders(prdContent);
        }

        @Test
        @DisplayName("PRD should have complete document structure")
        void shouldHaveCompleteDocumentStructure() {
            // Essential PRD sections
            String[] requiredSections = {
                "Executive Summary", "Problem Statement", "Solution Overview",
                "Functional Requirements", "Non-Functional Requirements",
                "Data Model", "API Specifications", "Deployment Architecture"
            };
            
            for (String section : requiredSections) {
                assertTrue(headers.stream().anyMatch(h -> h.contains(section)),
                    "PRD should contain section: " + section);
            }
        }

        @Test
        @DisplayName("PRD should define functional requirements with IDs")
        void shouldDefineFunctionalRequirementsWithIds() {
            Pattern frPattern = Pattern.compile("FR-[A-Z]+-\\d+:");
            Matcher matcher = frPattern.matcher(prdContent);
            
            Set<String> requirementIds = new HashSet<>();
            while (matcher.find()) {
                requirementIds.add(matcher.group());
            }
            
            assertTrue(requirementIds.size() >= MIN_FUNCTIONAL_REQUIREMENTS,
                "PRD should define at least " + MIN_FUNCTIONAL_REQUIREMENTS + " functional requirements");
            
            // Check for specific requirement categories
            assertTrue(requirementIds.stream().anyMatch(id -> id.startsWith("FR-CDC-")),
                "Should have CDC functional requirements");
            assertTrue(requirementIds.stream().anyMatch(id -> id.startsWith("FR-ETL-")),
                "Should have ETL functional requirements");
            assertTrue(requirementIds.stream().anyMatch(id -> id.startsWith("FR-AUTH-")),
                "Should have Authentication functional requirements");
        }

        @Test
        @DisplayName("PRD should define non-functional requirements with IDs")
        void shouldDefineNonFunctionalRequirementsWithIds() {
            Pattern nfrPattern = Pattern.compile("NFR-[A-Z]+-\\d+:");
            Matcher matcher = nfrPattern.matcher(prdContent);
            
            Set<String> nfrIds = new HashSet<>();
            while (matcher.find()) {
                nfrIds.add(matcher.group());
            }
            
            assertTrue(nfrIds.size() >= MIN_NONFUNCTIONAL_REQUIREMENTS,
                "PRD should define at least " + MIN_NONFUNCTIONAL_REQUIREMENTS + " non-functional requirements");
        }

        @Test
        @DisplayName("PRD should have API specifications with examples")
        void shouldHaveApiSpecificationsWithExamples() {
            assertTrue(prdContent.contains("POST /auth/signin"),
                "Should document signin API");
            assertTrue(prdContent.contains("POST /auth/signup"),
                "Should document signup API");
            assertTrue(prdContent.contains("POST /api/etl/process"),
                "Should document ETL process API");
            assertTrue(prdContent.contains("POST /api/cdc/start"),
                "Should document CDC start API");
            
            // Check for JSON examples
            assertTrue(prdContent.contains("```json"),
                "Should have JSON examples in API specs");
        }

        @Test
        @DisplayName("PRD should document database schema")
        void shouldDocumentDatabaseSchema() {
            assertTrue(prdContent.contains("CREATE TABLE users"),
                "Should document users table");
            assertTrue(prdContent.contains("CREATE TABLE roles"),
                "Should document roles table");
            assertTrue(prdContent.contains("CREATE TABLE processed_data"),
                "Should document processed_data table");
        }

        @Test
        @DisplayName("PRD should define success metrics")
        void shouldDefineSuccessMetrics() {
            String lowerContent = prdContent.toLowerCase();
            assertTrue(lowerContent.contains("metrics") || lowerContent.contains("kpi"),
                "PRD should define success metrics or KPIs");
            
            // Check for specific metric types
            assertTrue(prdContent.contains("%") || prdContent.contains("percent"),
                "Metrics should include percentage values");
        }

        @Test
        @DisplayName("PRD should have risk assessment")
        void shouldHaveRiskAssessment() {
            assertTrue(prdContent.toLowerCase().contains("risk"),
                "PRD should include risk assessment");
            assertTrue(prdContent.toLowerCase().contains("mitigation"),
                "PRD should include risk mitigation strategies");
        }
    }

    @Nested
    @DisplayName("ARCHITECTURE.md Tests")
    class ArchitectureTests {

        private String archContent;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws IOException {
            Path archPath = PROJECT_ROOT.resolve("ARCHITECTURE.md");
            assertTrue(Files.exists(archPath), "ARCHITECTURE.md should exist");
            archContent = readUtf8File(archPath);
        }

        @Test
        @DisplayName("Architecture doc should have system diagrams")
        void shouldHaveSystemDiagrams() {
            // Check for various diagram formats.
            boolean hasAsciiDiagram = archContent.contains("┌") || archContent.contains("│") ||
                                      archContent.contains("└") || archContent.contains("─");
            boolean hasMermaidDiagram = archContent.contains("```mermaid");
            boolean hasPlantUmlDiagram = archContent.contains("```plantuml");

            assertTrue(hasAsciiDiagram || hasMermaidDiagram || hasPlantUmlDiagram,
                "Should contain system diagrams (ASCII art, Mermaid, or PlantUML)");
        }

        @Test
        @DisplayName("Architecture doc should describe all services")
        void shouldDescribeAllServices() {
            String[] services = {
                "CDC Service", "ETL Service", "Zuul Gateway",
                "Eureka Server", "Config Server", "Zipkin"
            };
            
            for (String service : services) {
                assertTrue(archContent.contains(service),
                    "Architecture should describe " + service);
            }
        }

        @Test
        @DisplayName("Architecture doc should document data flows")
        void shouldDocumentDataFlows() {
            assertTrue(archContent.contains("Flow") || archContent.contains("flow"),
                "Should document data flows");
            assertTrue(archContent.contains("ETL Processing Flow") ||
                      archContent.contains("CDC Event Capture Flow"),
                "Should have specific flow diagrams");
        }

        @Test
        @DisplayName("Architecture doc should document security architecture")
        void shouldDocumentSecurityArchitecture() {
            assertTrue(archContent.toLowerCase().contains("security"),
                "Should have security section");
            assertTrue(archContent.contains("JWT") || archContent.contains("Authentication"),
                "Should document JWT authentication");
            assertTrue(archContent.contains("BCrypt") || archContent.contains("password"),
                "Should document password security");
        }

        @Test
        @DisplayName("Architecture doc should document monitoring")
        void shouldDocumentMonitoring() {
            String lowerContent = archContent.toLowerCase();
            assertTrue(lowerContent.contains("monitoring") || 
                      lowerContent.contains("observability"),
                "Should document monitoring/observability");
            assertTrue(archContent.contains("Zipkin") || archContent.contains("tracing"),
                "Should document distributed tracing");
        }

        @Test
        @DisplayName("Architecture doc should document deployment")
        void shouldDocumentDeployment() {
            assertTrue(archContent.toLowerCase().contains("deployment"),
                "Should have deployment section");
            assertTrue(archContent.contains("port") || archContent.contains("Port"),
                "Should document service ports");
        }

        @Test
        @DisplayName("Architecture doc should document technology integration")
        void shouldDocumentTechnologyIntegration() {
            String[] technologies = {
                "Debezium", "Kafka", "PostgreSQL", "Spring"
            };
            
            for (String tech : technologies) {
                assertTrue(archContent.contains(tech),
                    "Should document integration with " + tech);
            }
        }
    }

    @Nested
    @DisplayName("CHANGELOG.md Tests")
    class ChangelogTests {

        private String changelogContent;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws IOException {
            Path changelogPath = PROJECT_ROOT.resolve("CHANGELOG.md");
            assertTrue(Files.exists(changelogPath), "CHANGELOG.md should exist");
            changelogContent = readUtf8File(changelogPath);
        }

        @Test
        @DisplayName("Changelog should follow Keep a Changelog format")
        void shouldFollowKeepAChangelogFormat() {
            assertTrue(changelogContent.contains("# Changelog"),
                "Should have Changelog title");
            assertTrue(changelogContent.contains("keepachangelog.com"),
                "Should reference Keep a Changelog");
            assertTrue(changelogContent.contains("Semantic Versioning") ||
                      changelogContent.contains("semver.org"),
                "Should reference Semantic Versioning");
        }

        @Test
        @DisplayName("Changelog should have versioned releases")
        void shouldHaveVersionedReleases() {
            Pattern versionPattern = Pattern.compile("##\\s*\\[([0-9]+\\.[0-9]+\\.[0-9]+)\\]");
            Matcher matcher = versionPattern.matcher(changelogContent);
            
            List<String> versions = new ArrayList<>();
            while (matcher.find()) {
                versions.add(matcher.group(1));
            }
            
            assertFalse(versions.isEmpty(), "Should have at least one versioned release");
        }

        @Test
        @DisplayName("Changelog should categorize changes")
        void shouldCategorizeChanges() {
            String[] categories = {"Added", "Changed", "Fixed", "Removed", "Deprecated"};
            
            long categoriesFound = Arrays.stream(categories)
                .filter(cat -> changelogContent.contains("### " + cat))
                .count();
            
            assertTrue(categoriesFound > 0,
                "Should use standard change categories (Added, Changed, Fixed, etc.)");
        }

        @Test
        @DisplayName("Changelog should document documentation changes")
        void shouldDocumentDocumentationChanges() {
            assertTrue(changelogContent.toLowerCase().contains("documentation"),
                "Should document documentation changes");
            assertTrue(changelogContent.contains("README.md") ||
                      changelogContent.contains("PRD.md") ||
                      changelogContent.contains("ARCHITECTURE.md"),
                "Should mention specific documentation files");
        }
    }

    @Nested
    @DisplayName("SUMMARY_KR.md Tests")
    class SummaryKrTests {

        private String summaryKrContent;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws IOException {
            Path summaryPath = PROJECT_ROOT.resolve("SUMMARY_KR.md");
            assertTrue(Files.exists(summaryPath), "SUMMARY_KR.md should exist");
            summaryKrContent = readUtf8File(summaryPath);
        }

        @Test
        @DisplayName("Korean summary should have proper structure")
        void shouldHaveProperStructure() {
            assertFalse(summaryKrContent.trim().isEmpty(),
                "Korean summary should not be empty");
            assertTrue(summaryKrContent.contains("#"),
                "Should have markdown headers");
        }

        @Test
        @DisplayName("Korean summary should contain Korean characters")
        void shouldContainKoreanCharacters() {
            // Check for Hangul characters (Korean alphabet)
            assertTrue(Pattern.compile("[\\uAC00-\\uD7AF]").matcher(summaryKrContent).find(),
                "Korean summary should contain Korean characters");
        }

        @Test
        @DisplayName("Korean summary should reference main English docs")
        void shouldReferenceMainEnglishDocs() {
            assertTrue(summaryKrContent.contains("README.md") ||
                      summaryKrContent.contains("PRD.md") ||
                      summaryKrContent.contains("ARCHITECTURE.md"),
                "Korean summary should reference main documentation files");
        }
    }

    @Nested
    @DisplayName("Cross-Document Consistency Tests")
    class CrossDocumentTests {

        private Map<String, String> allDocs;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws IOException {
            allDocs = new HashMap<>();
            allDocs.put("README", readUtf8File(PROJECT_ROOT.resolve("README.md")));
            allDocs.put("PRD", readUtf8File(PROJECT_ROOT.resolve("PRD.md")));
            allDocs.put("ARCHITECTURE", readUtf8File(PROJECT_ROOT.resolve("ARCHITECTURE.md")));
            allDocs.put("CHANGELOG", readUtf8File(PROJECT_ROOT.resolve("CHANGELOG.md")));
        }

        @Test
        @DisplayName("Service ports should be consistent across documents")
        void servicePortsShouldBeConsistent() {
            Map<String, String> portMappings = new HashMap<>();
            portMappings.put("8080", "Zuul");
            portMappings.put("8000", "ETL");
            portMappings.put("8001", "CDC");
            portMappings.put("8761", "Eureka");
            
            for (Map.Entry<String, String> entry : portMappings.entrySet()) {
                String port = entry.getKey();
                String service = entry.getValue();
                
                // Check each document mentions the port
                for (Map.Entry<String, String> doc : allDocs.entrySet()) {
                    if (doc.getValue().contains(service)) {
                        assertTrue(doc.getValue().contains(port),
                            doc.getKey() + " should mention port " + port + " for " + service);
                    }
                }
            }
        }

        @Test
        @DisplayName("API endpoints should be consistent across documents")
        void apiEndpointsShouldBeConsistent() {
            String[] criticalEndpoints = {
                "/api/etl/process",
                "/api/cdc/start",
                "/api/cdc/stop",
                "/auth/signin",
                "/auth/signup"
            };
            
            for (String endpoint : criticalEndpoints) {
                long docsContainingEndpoint = allDocs.values().stream()
                    .filter(content -> content.contains(endpoint))
                    .count();
                
                assertTrue(docsContainingEndpoint >= 2,
                    "Endpoint " + endpoint + " should be documented in multiple files");
            }
        }

        @Test
        @DisplayName("Technology stack should be consistent")
        void technologyStackShouldBeConsistent() {
            String[] technologies = {
                "Debezium", "Kafka", "PostgreSQL", "Spring Boot",
                "JWT", "Eureka", "Zuul"
            };
            
            for (String tech : technologies) {
                long docsContainingTech = allDocs.values().stream()
                    .filter(content -> content.contains(tech))
                    .count();
                
                assertTrue(docsContainingTech >= 2,
                    "Technology " + tech + " should be mentioned in multiple documents");
            }
        }

        @Test
        @DisplayName("Version numbers should be consistent")
        void versionNumbersShouldBeConsistent() {
            // Extract version references from different docs
            Pattern versionPattern = Pattern.compile("(?:version|Version|v)[:\\s]*([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)");
            
            // Documents that can have multiple versions (e.g., changelogs, READMEs with historical info)
            Set<String> allowedMultiVersionDocs = new HashSet<>(Arrays.asList("README", "CHANGELOG"));
            
            Map<String, Set<String>> versionsByDoc = new HashMap<>();
            for (Map.Entry<String, String> doc : allDocs.entrySet()) {
                Set<String> versions = new HashSet<>();
                Matcher matcher = versionPattern.matcher(doc.getValue());
                while (matcher.find()) {
                    versions.add(matcher.group(1));
                }
                if (!versions.isEmpty()) {
                    versionsByDoc.put(doc.getKey(), versions);
                }
            }
            
            // Check per-document consistency: each doc should have consistent versions
            // except for those in the allowlist
            for (Map.Entry<String, Set<String>> entry : versionsByDoc.entrySet()) {
                if (!allowedMultiVersionDocs.contains(entry.getKey())) {
                    assertTrue(entry.getValue().size() == 1,
                        "Document " + entry.getKey() + " has inconsistent version numbers: " + entry.getValue());
                }
            }
        }
    }

    @Nested
    @DisplayName("Content Quality Tests")
    class ContentQualityTests {

        @ParameterizedTest
        @ValueSource(strings = {"README.md", "PRD.md", "ARCHITECTURE.md", "CHANGELOG.md"})
        @DisplayName("Documents should not have trailing whitespace")
        void shouldNotHaveExcessiveTrailingWhitespace(String filename) throws IOException {
            Path filePath = PROJECT_ROOT.resolve(filename);
            List<String> lines = Files.readAllLines(filePath);
            
            long linesWithTrailingSpaces = lines.stream()
                .filter(line -> line.endsWith(" ") || line.endsWith("\t"))
                .count();
            
            // Allow some trailing whitespace but flag if excessive
            assertTrue(linesWithTrailingSpaces < lines.size() * 0.1,
                filename + " should not have excessive trailing whitespace");
        }

        @ParameterizedTest
        @ValueSource(strings = {"README.md", "PRD.md", "ARCHITECTURE.md"})
        @DisplayName("Documents should have balanced code blocks")
        void shouldHaveBalancedCodeBlocks(String filename) throws IOException {
            Path filePath = PROJECT_ROOT.resolve(filename);
            String content = readUtf8File(filePath);
            
            long openingBlocks = Arrays.stream(content.split("\\r?\\n"))
                .filter(line -> line.trim().startsWith("```"))
                .count();
            
            assertTrue(openingBlocks % 2 == 0,
                filename + " should have balanced code blocks (even number of ```)");
        }

        @ParameterizedTest
        @ValueSource(strings = {"README.md", "PRD.md", "ARCHITECTURE.md"})
        @DisplayName("Documents should not have broken markdown links")
        void shouldNotHaveBrokenMarkdownLinks(String filename) throws IOException {
            Path filePath = PROJECT_ROOT.resolve(filename);
            String content = readUtf8File(filePath);
            
            // Check for common markdown link errors
            assertFalse(content.contains("](]"),
                filename + " should not have malformed links like ](]");
            assertFalse(content.contains("[]("),
                filename + " should not have empty link text []( ");
            
            // Check for unclosed reference-style links (must end with ] not followed by text)
            Pattern unclosedRefPattern = Pattern.compile("\\[[^\\]]+\\]\\s*\\[[^\\]]*$", Pattern.MULTILINE);
            assertFalse(unclosedRefPattern.matcher(content).find(),
                filename + " should not have unclosed reference-style links");
        }

        @ParameterizedTest
        @ValueSource(strings = {"README.md", "PRD.md", "ARCHITECTURE.md", "CHANGELOG.md"})
        @DisplayName("Documents should have consistent header hierarchy")
        void shouldHaveConsistentHeaderHierarchy(String filename) throws IOException {
            Path filePath = PROJECT_ROOT.resolve(filename);
            String content = readUtf8File(filePath);
            
            List<Integer> headerLevels = new ArrayList<>();
            Pattern headerPattern = Pattern.compile("^(#{1,6})\\s+", Pattern.MULTILINE);
            Matcher matcher = headerPattern.matcher(content);
            
            while (matcher.find()) {
                headerLevels.add(matcher.group(1).length());
            }
            
            // Check for reasonable header progression
            if (!headerLevels.isEmpty()) {
                assertEquals(1, headerLevels.get(0).intValue(),
                    filename + " should start with h1 header");
                
                // Check no massive jumps (e.g., h1 to h6)
                for (int i = 1; i < headerLevels.size(); i++) {
                    int diff = Math.abs(headerLevels.get(i) - headerLevels.get(i - 1));
                    assertTrue(diff <= 2,
                        filename + " should not have header level jumps > 2");
                }
            }
        }
    }

    // Helper methods

    private List<String> extractHeaders(String content) {
        List<String> headers = new ArrayList<>();
        Matcher matcher = MARKDOWN_HEADER_PATTERN.matcher(content);
        while (matcher.find()) {
            headers.add(matcher.group(1));
        }
        return headers;
    }
}
