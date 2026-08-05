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
 * Specifies the additive Flyway contract for exact durable-job lease fencing.
 */
class EtlJobLeaseMigrationTest {

    @Test
    void addsDescriptiveLeaseColumnsAndEligibilityIndex() throws IOException {
        String migration = readMigration();

        assertTrue(migration.contains("ADD COLUMN lease_claim_id UUID"));
        assertTrue(migration.contains("ADD COLUMN lease_owner_id VARCHAR(128)"));
        assertTrue(migration.contains("ADD COLUMN lease_expires_at TIMESTAMPTZ"));
        assertTrue(migration.contains("CREATE INDEX etl_job_claim_eligibility_index"));
        assertTrue(migration.contains(
                "(job_status, lease_expires_at, created_at, job_record_id)"
        ));
        assertFalse(migration.contains(" ADD COLUMN owner "));
        assertFalse(migration.contains(" ADD COLUMN lease "));
    }

    @Test
    void requiresLeaseFieldsOnlyForRunningRows() throws IOException {
        String migration = normalize(readMigration());

        assertTrue(migration.contains("CONSTRAINT etl_job_lease_lifecycle_check"));
        assertTrue(migration.contains(
                "job_status = 'RUNNING' AND lease_claim_id IS NOT NULL AND lease_owner_id IS NOT NULL AND lease_expires_at IS NOT NULL"
        ));
        assertTrue(migration.contains(
                "job_status <> 'RUNNING' AND lease_claim_id IS NULL AND lease_owner_id IS NULL AND lease_expires_at IS NULL"
        ));
    }

    @Test
    void requiresFailureCodesOnlyForFailedRows() throws IOException {
        String migration = normalize(readMigration());

        assertTrue(migration.contains("CONSTRAINT etl_job_failure_lifecycle_check"));
        assertTrue(migration.contains("job_status = 'FAILED' AND failure_code IS NOT NULL"));
        assertTrue(migration.contains("job_status <> 'FAILED' AND failure_code IS NULL"));
    }

    private static String readMigration() throws IOException {
        return Files.readString(
                projectRoot().resolve(
                        "etl-service/src/main/resources/db/migration/"
                                + "V3__add_etl_job_lease_fencing.sql"
                ),
                StandardCharsets.UTF_8
        );
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
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
