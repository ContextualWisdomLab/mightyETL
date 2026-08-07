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
 * Guards nonblocking PostgreSQL indexes for replay descendant and foreign-key lookups.
 *
 * <p>The lineage trigger checks descendants whenever durable terminal evidence changes. Without
 * indexes beginning with the source and root identifiers, ordinary lifecycle updates can degrade
 * into full-table scans as durable-job history grows. The indexes are therefore isolated from the
 * transactional lineage schema and built concurrently.</p>
 */
class EtlJobReplayLookupIndexMigrationTest {

    private static final String V7_MIGRATION =
            "etl-service/src/main/resources/db/migration/V7__add_etl_job_replay_lineage.sql";
    private static final String V8_MIGRATION =
            "etl-service/src/main/resources/db/migration/V8__add_etl_job_replay_lookup_indexes.sql";
    private static final String V8_CONFIGURATION = V8_MIGRATION + ".conf";

    @Test
    void separatesTransactionalLineageFromConcurrentLookupIndexes() throws IOException {
        String lineageMigration = normalize(read(V7_MIGRATION));
        String indexMigration = normalize(read(V8_MIGRATION));

        assertFalse(
                lineageMigration.contains("CREATE INDEX"),
                "the transactional lineage migration must not contain a production index build"
        );
        assertTrue(indexMigration.contains(
                "CREATE INDEX CONCURRENTLY etl_job_replay_source_lookup_index"
        ));
        assertTrue(indexMigration.contains(
                "ON etl_job_records ( replay_source_job_record_id, principal_scope_hash )"
        ));
        assertTrue(indexMigration.contains(
                "WHERE replay_source_job_record_id IS NOT NULL"
        ));
        assertTrue(indexMigration.contains(
                "CREATE INDEX CONCURRENTLY etl_job_replay_root_lookup_index"
        ));
        assertTrue(indexMigration.contains(
                "ON etl_job_records ( replay_root_job_record_id, principal_scope_hash )"
        ));
        assertTrue(indexMigration.contains(
                "WHERE replay_root_job_record_id IS NOT NULL"
        ));
    }

    @Test
    void disablesFlywayTransactionForConcurrentReplayIndexes() throws IOException {
        Path configurationPath = projectRoot().resolve(V8_CONFIGURATION);
        String applicationProperties = read(
                "etl-service/src/main/resources/application.properties"
        );

        assertTrue(
                Files.exists(configurationPath),
                "the concurrent replay-index migration requires a Flyway script configuration"
        );
        assertTrue(
                Files.readString(configurationPath, StandardCharsets.UTF_8)
                        .contains("executeInTransaction=false")
        );
        assertTrue(applicationProperties.contains(
                "spring.flyway.postgresql.transactional-lock=false"
        ));
    }

    @Test
    void verifierRequiresReadyAndValidReplayLookupIndexes() throws IOException {
        String verifier = normalize(read("scripts/verify-postgresql-migrations.sh"));

        assertTrue(verifier.contains("etl_job_replay_source_lookup_index"));
        assertTrue(verifier.contains("etl_job_replay_root_lookup_index"));
        assertTrue(verifier.contains("index_record.indisready"));
        assertTrue(verifier.contains("index_record.indisvalid"));
        assertTrue(verifier.contains("replay lookup indexes are missing or invalid"));
    }

    @Test
    void runbookDocumentsConcurrentFailureRecoveryAndRollback() throws IOException {
        String runbook = normalize(read("docs/operations/durable-job-replay.md"));

        assertTrue(runbook.contains("V8__add_etl_job_replay_lookup_indexes.sql"));
        assertTrue(runbook.contains("CREATE INDEX CONCURRENTLY"));
        assertTrue(runbook.contains("invalid index"));
        assertTrue(runbook.contains(
                "DROP INDEX CONCURRENTLY etl_job_replay_source_lookup_index"
        ));
        assertTrue(runbook.contains(
                "DROP INDEX CONCURRENTLY etl_job_replay_root_lookup_index"
        ));
        assertTrue(runbook.contains("executeInTransaction=false"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * Finds the reactor root from repository-root or module-local Maven execution.
     *
     * @return repository root containing migrations, scripts, and documentation
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
