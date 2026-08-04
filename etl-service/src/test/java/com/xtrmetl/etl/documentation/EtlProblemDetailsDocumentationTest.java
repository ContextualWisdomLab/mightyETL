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
 * Keeps operator documentation aligned with the ETL RFC 9457 response contract.
 */
class EtlProblemDetailsDocumentationTest {

    private static final List<String> ERROR_CODES = List.of(
            "etl_payload_too_large",
            "etl_batch_too_large",
            "etl_invalid_json",
            "etl_invalid_record",
            "etl_invalid_idempotency_key",
            "etl_idempotency_principal_required",
            "etl_idempotency_key_reused",
            "etl_target_unavailable",
            "etl_target_failure",
            "etl_internal_error"
    );

    @Test
    void apiRunbookDocumentsTheCompleteContract() throws IOException {
        String runbook = read("docs/api/problem-details.md");

        assertTrue(runbook.contains("RFC 9457"));
        assertTrue(runbook.contains("application/problem+json"));
        assertTrue(runbook.contains("urn:mightyetl:problem:"));
        assertTrue(runbook.contains("Successful `POST /api/etl/process` responses remain"));
        assertTrue(runbook.contains("never includes internal exception text"));
        assertTrue(runbook.contains("does not make retries idempotent"));
        assertTrue(runbook.contains("Idempotency-Replayed"));
        assertTrue(runbook.contains("docs/etl/idempotent-retries.md"));
        for (String errorCode : ERROR_CODES) {
            assertTrue(runbook.contains(errorCode), "Missing problem code: " + errorCode);
        }
    }

    @Test
    void idempotencyRunbookUsesTheAuthenticationMechanismImplementedByTheService() throws IOException {
        String runbook = read("docs/etl/idempotent-retries.md");

        assertTrue(runbook.contains("Authorization: Basic <credentials>"));
        assertTrue(runbook.contains("Spring Security HTTP Basic"));
        assertFalse(runbook.contains("Authorization: Bearer <token>"));
    }

    @Test
    void idempotencyRunbookDocumentsAdmissionBeforeLedgerAccess() throws IOException {
        String runbook = read("docs/etl/idempotent-retries.md");

        assertTrue(runbook.contains("413 etl_payload_too_large"));
        assertTrue(runbook.contains("before request-lock or ledger access"));
        assertTrue(runbook.contains("configured UTF-8 payload bound"));
    }

    @Test
    void publicServiceDocumentationUsesTheCurrentStructuredFieldsStandard() throws IOException {
        String serviceSource = read(
                "etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java"
        );

        assertTrue(serviceSource.contains("RFC 9651"));
        assertFalse(serviceSource.contains("RFC 8941"));
    }

    @Test
    void localComposeBaselinesTheKnownBootstrapSchemaBeforeMigrationOne() throws IOException {
        String compose = read("docker-compose.yml");
        String runbook = read("docs/etl/idempotent-retries.md");

        assertTrue(compose.contains("FLYWAY_BASELINE_ON_MIGRATE: \"true\""));
        assertTrue(runbook.contains("local Compose stack sets `FLYWAY_BASELINE_ON_MIGRATE=true`"));
        assertTrue(runbook.contains("baseline version remains `0`"));
    }

    @Test
    void readmeLinksTheContractAndRetryBoundary() throws IOException {
        String readme = read("README.md");

        assertTrue(readme.contains("docs/api/problem-details.md"));
        assertTrue(readme.contains("application/problem+json"));
        assertTrue(readme.contains("400/413/422"));
        assertTrue(readme.contains("503"));
        assertTrue(readme.contains("Do not automatically retry `500`"));
    }

    @Test
    void changelogRecordsTheCompatibilityChange() throws IOException {
        String changelog = read("CHANGELOG.md");

        assertTrue(changelog.contains("RFC 9457"));
        assertTrue(changelog.contains("stable `errorCode`"));
        assertTrue(changelog.contains("internal exception text"));
        assertTrue(changelog.contains("docs/api/problem-details.md"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

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
