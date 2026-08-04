package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines the PostgreSQL lease, retry, and terminal-summary schema contract.
 */
class EtlJobWorkerMigrationTest {

    private static final Pattern ONE_WORD_OBJECT = Pattern.compile(
            "(?im)^(?:CREATE\\s+(?:TABLE|INDEX)|CONSTRAINT)\\s+[a-z]+(?:\\s|\\()"
    );

    @Test
    void migrationAddsFencedLeaseAndRetryStateWithDescriptiveNames() throws IOException {
        String migration = Files.readString(
                projectRoot().resolve(
                        "etl-service/src/main/resources/db/migration/"
                                + "V3__add_etl_job_worker_leases.sql"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(migration.contains("lease_token UUID"));
        assertTrue(migration.contains("lease_expires_at TIMESTAMPTZ"));
        assertTrue(migration.contains("next_attempt_at TIMESTAMPTZ NOT NULL"));
        assertTrue(migration.contains("processed_record_count INTEGER"));
        assertTrue(migration.contains("etl_job_running_lease_state_check"));
        assertTrue(migration.contains("etl_job_processed_record_count_check"));
        assertTrue(migration.contains("etl_job_failure_state_check"));
        assertTrue(migration.contains("etl_job_worker_queue_index"));
        assertTrue(migration.contains("WHERE job_status IN ('PENDING', 'RUNNING')"));
        assertTrue(migration.contains("job_status = 'RUNNING'"));
        assertTrue(migration.contains("lease_token IS NOT NULL"));
        assertTrue(migration.contains("lease_expires_at IS NOT NULL"));
        assertTrue(migration.contains("job_status = 'SUCCEEDED'"));
        assertTrue(migration.contains("processed_record_count IS NOT NULL"));
        assertTrue(migration.contains("job_status = 'FAILED'"));
        assertTrue(migration.contains("failure_code IS NOT NULL"));
        assertFalse(ONE_WORD_OBJECT.matcher(migration).find());
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
