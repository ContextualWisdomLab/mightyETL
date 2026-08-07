package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards database-level digest continuity for immutable durable-job replay lineage.
 *
 * <p>Application admission already compares the resupplied payload digest with the terminal
 * source. The database trigger is an independent integrity boundary for imports and other direct
 * writers, so every derived row must retain the exact immediate source request digest.</p>
 */
class EtlJobReplayDigestContinuityMigrationTest {

    @Test
    void lineageTriggerRequiresImmediateSourceDigestEquality() throws IOException {
        String migration = normalize(read(
                "etl-service/src/main/resources/db/migration/V7__add_etl_job_replay_lineage.sql"
        ));

        assertTrue(migration.contains("source_request_digest"));
        assertTrue(migration.contains("request_digest"));
        assertTrue(migration.contains(
                "source_request_digest IS DISTINCT FROM NEW.request_digest"
        ));
        assertTrue(migration.contains("Replay request digest must match the immediate source"));
    }

    @Test
    void postgresqlRehearsalRejectsMismatchedReplayDigest() throws IOException {
        String rehearsal = normalize(read(
                "etl-service/src/test/postgresql/replay_lineage_migration.sql"
        ));

        assertTrue(rehearsal.contains("digest_continuity_check"));
        assertTrue(rehearsal.contains("replay digest mismatch was accepted"));
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
     * @return repository root containing migrations and integration fixtures
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
