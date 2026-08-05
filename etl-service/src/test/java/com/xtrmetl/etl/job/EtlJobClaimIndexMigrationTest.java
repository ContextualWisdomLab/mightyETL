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
 * Guards the production rollout contract for the durable-job claim eligibility index.
 *
 * <p>The table can already contain accepted work when lease fencing is introduced. PostgreSQL's
 * regular index build blocks inserts, updates, and deletes, so the claim index must be isolated in
 * a non-transactional concurrent migration while the lease columns and constraints remain in the
 * transactional V3 migration.</p>
 */
class EtlJobClaimIndexMigrationTest {

    private static final String V3_MIGRATION =
            "etl-service/src/main/resources/db/migration/V3__add_etl_job_lease_fencing.sql";
    private static final String V4_MIGRATION =
            "etl-service/src/main/resources/db/migration/V4__add_etl_job_claim_eligibility_index.sql";
    private static final String V4_CONFIGURATION = V4_MIGRATION + ".conf";

    @Test
    void keepsTransactionalLeaseSchemaSeparateFromConcurrentIndexBuild() throws IOException {
        String leaseMigration = normalize(read(V3_MIGRATION));
        String indexMigration = normalize(read(V4_MIGRATION));

        assertFalse(
                leaseMigration.contains("CREATE INDEX"),
                "the transactional lease migration must not contain a production index build"
        );
        assertTrue(indexMigration.contains(
                "CREATE INDEX CONCURRENTLY etl_job_claim_eligibility_index"
        ));
        assertTrue(indexMigration.contains(
                "ON etl_job_records (job_status, lease_expires_at, created_at, job_record_id)"
        ));
    }

    @Test
    void disablesFlywayTransactionsAndPostgresqlTransactionalLocksForConcurrentDdl()
            throws IOException {
        Path configurationPath = projectRoot().resolve(V4_CONFIGURATION);
        String application = normalize(
                read("etl-service/src/main/resources/application.yml")
        );

        assertTrue(
                Files.exists(configurationPath),
                "the concurrent migration requires a matching Flyway script configuration"
        );
        assertTrue(
                Files.readString(configurationPath, StandardCharsets.UTF_8)
                        .contains("executeInTransaction=false")
        );
        assertTrue(application.contains("postgresql: transactional-lock: false"));
    }

    @Test
    void runbookDocumentsConcurrentFailureRecoveryAndRollback() throws IOException {
        String runbook = normalize(read("docs/operations/durable-job-worker.md"));

        assertTrue(runbook.contains("V4__add_etl_job_claim_eligibility_index.sql"));
        assertTrue(runbook.contains("CREATE INDEX CONCURRENTLY"));
        assertTrue(runbook.contains("invalid index"));
        assertTrue(runbook.contains(
                "DROP INDEX CONCURRENTLY etl_job_claim_eligibility_index"
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
     * Finds the reactor root from either repository-root or module-local Maven execution.
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
