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
 *
 * <p>The ordinary cross-platform Java build validates these repository contracts without
 * requiring PostgreSQL on every runner. The separate direct-{@code develop} integration
 * workflow then executes the same verifier against PostgreSQL 18.</p>
 */
class EtlJobReplayPostgresWorkflowTest {

    @Test
    void directDevelopWorkflowRunsCompleteReplayMigrationChainOnPostgres18() throws IOException {
        String workflow = read(
                ".github/workflows/postgresql-migration-integration.yml"
        ).replaceAll("\\s+", " ");
        String verifier = read(
                "scripts/verify-postgresql-migrations.sh"
        ).replaceAll("\\s+", " ");

        assertTrue(workflow.contains("replay_lineage_migration:"));
        assertTrue(workflow.contains("image: postgres:18-alpine"));
        assertTrue(workflow.contains("--health-cmd \"pg_isready"));
        assertTrue(workflow.contains("etl-service/src/test/postgresql/**"));
        assertTrue(workflow.contains("bash scripts/verify-postgresql-migrations.sh"));

        assertTrue(verifier.contains("find \"${migration_directory}\""));
        assertTrue(verifier.contains("-name 'V*__*.sql'"));
        assertTrue(verifier.contains("sort -zV"));
        assertTrue(verifier.contains("psql --no-psqlrc --set ON_ERROR_STOP=1"));
        assertTrue(verifier.contains(
                "etl-service/src/test/postgresql/replay_lineage_migration.sql"
        ));
        assertTrue(verifier.contains(
                "pg_get_indexdef(index_record.indexrelid, 1, true)"
        ));
        assertTrue(verifier.contains(
                "pg_get_expr(index_record.indpred, index_record.indrelid)"
        ));
        assertTrue(verifier.contains("index_record.indnkeyatts = 1"));
        assertTrue(verifier.contains("index_record.indnatts = 1"));
        assertTrue(verifier.contains("NOT index_record.indisunique"));
        assertTrue(verifier.contains(
                "pg_get_indexdef(index_record.indexrelid, 1, true) = "
                        + "'replay_source_job_record_id'"
        ));
        assertTrue(verifier.contains(
                "pg_get_expr(index_record.indpred, index_record.indrelid) = "
                        + "'(replay_source_job_record_id IS NOT NULL)'"
        ));
        assertTrue(verifier.contains(
                "pg_get_indexdef(index_record.indexrelid, 1, true) = "
                        + "'replay_root_job_record_id'"
        ));
        assertTrue(verifier.contains(
                "pg_get_expr(index_record.indpred, index_record.indrelid) = "
                        + "'(replay_root_job_record_id IS NOT NULL)'"
        ));
    }

    @Test
    void postgresRehearsalCoversLineageContinuityTenantIntegrityAndRollback()
            throws IOException {
        String rehearsal = read(
                "etl-service/src/test/postgresql/replay_lineage_migration.sql"
        ).replaceAll("\\s+", " ");

        assertTrue(rehearsal.contains("replay lineage trigger or function is missing"));
        assertTrue(rehearsal.contains("nonterminal replay source was accepted"));
        assertTrue(rehearsal.contains("generation-one replay accepted a different root"));
        assertTrue(rehearsal.contains("a derived replay row was accepted as lineage root"));
        assertTrue(rehearsal.contains("a skipped replay generation was accepted"));
        assertTrue(rehearsal.contains("cross-owner source lineage was accepted"));
        assertTrue(rehearsal.contains("cross-owner root lineage was accepted"));
        assertTrue(rehearsal.contains("replay lineage fields were mutable"));
        assertTrue(rehearsal.contains("referenced replay root evidence was mutable"));
        assertTrue(rehearsal.contains("referenced immediate-source evidence was mutable"));
        assertTrue(rehearsal.contains(
                "ON DELETE RESTRICT did not protect immediate replay history"
        ));
        assertTrue(rehearsal.contains(
                "ON DELETE RESTRICT did not protect replay root history"
        ));
        assertTrue(rehearsal.contains(
                "DROP TRIGGER etl_job_replay_lineage_guard_trigger ON etl_job_records"
        ));
        assertTrue(rehearsal.contains(
                "DROP FUNCTION validate_etl_job_replay_lineage()"
        ));
        assertTrue(rehearsal.contains(
                "ALTER TABLE etl_job_records DROP CONSTRAINT etl_job_replay_source_reference"
        ));
        assertTrue(rehearsal.contains(
                "ALTER TABLE etl_job_records DROP COLUMN replay_source_job_record_id"
        ));
        assertTrue(rehearsal.contains("ROLLBACK"));
        assertTrue(rehearsal.contains("rollback rehearsal did not restore V7"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * Finds the repository root from reactor-root or module-local Maven execution.
     *
     * @return repository root that contains workflows, scripts, and test fixtures
     */
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
