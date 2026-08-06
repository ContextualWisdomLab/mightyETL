package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the production-PostgreSQL replay migration and rollback rehearsal executable in CI.
 */
class EtlJobReplayPostgresWorkflowTest {

    @Test
    void ciRunsTheCompleteReplayMigrationChainAgainstPostgres18() throws IOException {
        String workflow = read(".github/workflows/ci.yml").replaceAll("\\s+", " ");

        assertTrue(workflow.contains("postgres_replay_migration:"));
        assertTrue(workflow.contains("image: postgres:18"));
        assertTrue(workflow.contains("--health-cmd pg_isready"));
        assertTrue(workflow.contains("V2__create_etl_job_records.sql"));
        assertTrue(workflow.contains("V3__add_etl_job_lease_fencing.sql"));
        assertTrue(workflow.contains("V4__add_etl_job_claim_eligibility_index.sql"));
        assertTrue(workflow.contains("V5__add_etl_job_owner_pagination_index.sql"));
        assertTrue(workflow.contains("V6__add_etl_job_cancellation.sql"));
        assertTrue(workflow.contains("V7__add_etl_job_replay_lineage.sql"));
        assertTrue(workflow.contains("psql -v ON_ERROR_STOP=1"));
        assertTrue(workflow.contains(
                "etl-service/src/test/postgresql/replay_lineage_migration.sql"
        ));
    }

    @Test
    void postgresRehearsalCoversTenantIntegrityDeletionAndRollback() throws IOException {
        String rehearsal = read(
                "etl-service/src/test/postgresql/replay_lineage_migration.sql"
        ).replaceAll("\\s+", " ");

        assertTrue(rehearsal.contains("cross-owner source lineage was accepted"));
        assertTrue(rehearsal.contains("cross-owner root lineage was accepted"));
        assertTrue(rehearsal.contains("ON DELETE RESTRICT did not protect replay history"));
        assertTrue(rehearsal.contains("ALTER TABLE etl_job_records DROP CONSTRAINT etl_job_replay_source_reference"));
        assertTrue(rehearsal.contains("ALTER TABLE etl_job_records DROP COLUMN replay_source_job_record_id"));
        assertTrue(rehearsal.contains("ROLLBACK"));
        assertTrue(rehearsal.contains("rollback rehearsal did not restore V7"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /** @return repository root from reactor-root or module-local execution */
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
