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
 * Guards the complete, bounded, owner-scoped, non-cascading durable job replay lineage schema.
 */
class EtlJobReplayMigrationTest {

    @Test
    void replayMigrationAddsCompleteRestrictedOwnerScopedLineage() throws IOException {
        String migration = Files.readString(
                projectRoot().resolve(
                        "etl-service/src/main/resources/db/migration/"
                                + "V7__add_etl_job_replay_lineage.sql"
                ),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", " ");

        assertTrue(migration.contains("ADD COLUMN replay_source_job_record_id UUID"));
        assertTrue(migration.contains("ADD COLUMN replay_root_job_record_id UUID"));
        assertTrue(migration.contains("ADD COLUMN replay_generation_count INTEGER"));
        assertTrue(migration.contains("CONSTRAINT etl_job_owner_identity_unique"));
        assertTrue(migration.contains(
                "UNIQUE (job_record_id, principal_scope_hash)"
        ));
        assertTrue(migration.contains("CONSTRAINT etl_job_replay_source_reference"));
        assertTrue(migration.contains(
                "FOREIGN KEY (replay_source_job_record_id, principal_scope_hash) "
                        + "REFERENCES etl_job_records (job_record_id, principal_scope_hash) "
                        + "ON DELETE RESTRICT"
        ));
        assertTrue(migration.contains("CONSTRAINT etl_job_replay_root_reference"));
        assertTrue(migration.contains(
                "FOREIGN KEY (replay_root_job_record_id, principal_scope_hash) "
                        + "REFERENCES etl_job_records (job_record_id, principal_scope_hash) "
                        + "ON DELETE RESTRICT"
        ));
        assertTrue(migration.contains("CONSTRAINT etl_job_replay_lineage_complete_check"));
        assertTrue(migration.contains("replay_source_job_record_id IS NULL"));
        assertTrue(migration.contains("replay_root_job_record_id IS NULL"));
        assertTrue(migration.contains("replay_generation_count IS NULL"));
        assertTrue(migration.contains("replay_source_job_record_id IS NOT NULL"));
        assertTrue(migration.contains("replay_root_job_record_id IS NOT NULL"));
        assertTrue(migration.contains("replay_generation_count BETWEEN 1 AND 100"));
        assertTrue(migration.contains("replay_source_job_record_id <> job_record_id"));
        assertTrue(migration.contains("replay_root_job_record_id <> job_record_id"));
        assertFalse(migration.contains(
                "FOREIGN KEY (replay_source_job_record_id) "
                        + "REFERENCES etl_job_records (job_record_id)"
        ));
        assertFalse(migration.contains(
                "FOREIGN KEY (replay_root_job_record_id) "
                        + "REFERENCES etl_job_records (job_record_id)"
        ));
        assertFalse(migration.contains("ON DELETE CASCADE"));
        assertFalse(migration.contains("replay_payload"));
        assertFalse(migration.contains("principal_name"));
    }

    /** @return reactor root from repository-root or module-local execution */
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
