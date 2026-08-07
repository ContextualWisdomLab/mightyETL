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
 * Guards the complete, bounded, owner-scoped, non-cascading durable job replay lineage schema.
 */
class EtlJobReplayMigrationTest {

    @Test
    void replayMigrationAddsCompleteRestrictedOwnerScopedLineage() throws IOException {
        String migration = normalizedMigration();

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
        assertTrue(migration.contains(
                "CREATE FUNCTION validate_etl_job_replay_lineage() RETURNS trigger"
        ));
        assertTrue(migration.contains(
                "CREATE TRIGGER etl_job_replay_lineage_guard_trigger"
        ));
        assertTrue(migration.contains(
                "BEFORE INSERT OR UPDATE OF replay_source_job_record_id, "
                        + "replay_root_job_record_id, replay_generation_count, job_status, "
                        + "request_digest, request_payload, attempt_count, failure_code, "
                        + "cancellation_key_hash, cancellation_code, job_cancelled_at, "
                        + "created_at, updated_at"
        ));
        assertTrue(migration.contains(
                "NEW.replay_source_job_record_id <> NEW.replay_root_job_record_id"
        ));
        assertTrue(migration.contains(
                "source_generation_count IS DISTINCT FROM NEW.replay_generation_count - 1"
        ));
        assertTrue(migration.contains("Replay lineage fields are immutable"));
        assertTrue(migration.contains("Referenced replay evidence is immutable"));
        assertTrue(migration.contains("FOR UPDATE"));
        assertTrue(migration.contains(
                "child_record.replay_source_job_record_id = OLD.job_record_id"
        ));
        assertTrue(migration.contains(
                "child_record.replay_root_job_record_id = OLD.job_record_id"
        ));
        assertFalse(migration.contains("FOR KEY SHARE"));
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

    @Test
    void updateGuardAvoidsChildToAncestorLockInversion() throws IOException {
        String migration = normalizedMigration();
        Pattern updateReturnsBeforeInsertValidation = Pattern.compile(
                "IF TG_OP = 'UPDATE' THEN .*Referenced replay evidence is immutable.*"
                        + "RETURN NEW; END IF; IF NEW\\.replay_generation_count IS NULL"
        );
        Pattern descendantLookupTakesRowLock = Pattern.compile(
                "FROM etl_job_records AS child_record .*FOR UPDATE;.*IF FOUND THEN"
        );

        assertTrue(
                updateReturnsBeforeInsertValidation.matcher(migration).find(),
                "UPDATE validation must return before INSERT-only source/root locking"
        );
        assertFalse(
                descendantLookupTakesRowLock.matcher(migration).find(),
                "Referenced-child existence checks must not lock child rows in reverse order"
        );
    }

    private static String normalizedMigration() throws IOException {
        return Files.readString(
                projectRoot().resolve(
                        "etl-service/src/main/resources/db/migration/"
                                + "V7__add_etl_job_replay_lineage.sql"
                ),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", " ");
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
