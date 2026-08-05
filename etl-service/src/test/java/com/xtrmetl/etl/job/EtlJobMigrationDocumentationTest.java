package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the durable job schema and operator contract aligned with privacy and naming requirements.
 */
class EtlJobMigrationDocumentationTest {

    @Test
    void migrationUsesDescriptiveSnakeCaseObjectsAndPrincipalScopedUniqueness() throws IOException {
        String migration = read(
                "etl-service/src/main/resources/db/migration/V2__create_etl_job_records.sql"
        );

        assertTrue(migration.contains("CREATE TABLE etl_job_records"));
        assertTrue(migration.contains("job_record_id"));
        assertTrue(migration.contains("principal_scope_hash"));
        assertTrue(migration.contains("submission_key_hash"));
        assertTrue(migration.contains("request_digest"));
        assertTrue(migration.contains("request_payload TEXT,"));
        assertTrue(migration.contains("CONSTRAINT etl_job_submission_scope_unique"));
        assertTrue(migration.contains("UNIQUE (principal_scope_hash, submission_key_hash)"));
        assertTrue(migration.contains("etl_job_status_created_index"));
        assertFalse(migration.contains("principal_name"));
        assertFalse(migration.contains("idempotency_key TEXT"));
    }

    @Test
    void migrationReservesStableWorkerStatesAndRequiresTerminalPayloadClearing() throws IOException {
        String migration = read(
                "etl-service/src/main/resources/db/migration/V2__create_etl_job_records.sql"
        );

        assertTrue(migration.contains("'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'"));
        assertFalse(migration.contains("'PROCESSING'"));
        assertTrue(migration.contains("CONSTRAINT etl_job_payload_lifecycle_check"));
        assertTrue(migration.contains("job_status IN ('PENDING', 'RUNNING')"));
        assertTrue(migration.contains("request_payload IS NOT NULL"));
        assertTrue(migration.contains("job_status IN ('SUCCEEDED', 'FAILED')"));
        assertTrue(migration.contains("request_payload IS NULL"));
    }

    @Test
    void paginationMigrationUsesTheOwnerAndCompleteStableOrderingKey() throws IOException {
        String migration = read(
                "etl-service/src/main/resources/db/migration/"
                        + "V4__add_etl_job_owner_pagination_index.sql"
        ).replaceAll("\\s+", " ");

        assertTrue(migration.contains("CREATE INDEX etl_job_owner_pagination_index"));
        assertTrue(migration.contains(
                "ON etl_job_records ( principal_scope_hash, created_at DESC, job_record_id DESC )"
        ));
        assertFalse(migration.contains(" OFFSET "));
        assertFalse(migration.contains("principal_name"));
    }

    @Test
    void runbookDocumentsAcceptedSemanticsOwnershipAndLeaseFencedExecution() throws IOException {
        String runbook = read("docs/etl/durable-job-intake.md").replaceAll("\\s+", " ");

        assertTrue(runbook.contains("202 Accepted"));
        assertTrue(runbook.contains("Location: /api/etl/jobs/{job_record_id}"));
        assertTrue(runbook.contains("same authenticated principal"));
        assertTrue(runbook.contains("byte-for-byte identical JSON text"));
        assertTrue(runbook.contains("lease-fenced worker claims accepted jobs"));
        assertTrue(runbook.contains("PostgreSQL, not scheduler uniqueness, distributes work"));
        assertTrue(runbook.contains("same transaction"));
        assertTrue(runbook.contains("request payload"));
        assertTrue(runbook.contains("Cache-Control: no-store"));
        assertTrue(runbook.contains("422 etl_job_submission_key_reused"));
        assertTrue(runbook.contains("fail-closed"));
        assertTrue(runbook.contains("mightyetl.etl.jobs.intake-enabled=false"));
        assertTrue(runbook.contains("mightyetl.etl.jobs.worker.enabled=false"));
        assertTrue(runbook.contains("xtrmetl.*"));
    }

    @Test
    void runbookDocumentsOwnerScopedKeysetPaginationAndRollback() throws IOException {
        String runbook = read("docs/etl/durable-job-intake.md").replaceAll("\\s+", " ");

        assertTrue(runbook.contains("GET /api/etl/jobs?limit=50"));
        assertTrue(runbook.contains("created_at DESC, job_record_id DESC"));
        assertTrue(runbook.contains("strict tuple boundary"));
        assertTrue(runbook.contains("Link: <"));
        assertTrue(runbook.contains("rel=\"next\""));
        assertTrue(runbook.contains("etl_invalid_job_page_limit"));
        assertTrue(runbook.contains("etl_invalid_job_page_cursor"));
        assertTrue(runbook.contains("V4__add_etl_job_owner_pagination_index.sql"));
        assertTrue(runbook.contains("DROP INDEX etl_job_owner_pagination_index"));
        assertTrue(runbook.contains("RFC 8288"));
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
