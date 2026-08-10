package com.xtrmetl.etl.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines the PostgreSQL major-version compatibility boundary for restore rehearsals.
 */
class PostgresLogicalRestoreVersionCompatibilityTest {

    @Test
    void restoreRehearsalRejectsMajorVersionMismatchBeforeDatabaseWrites() throws IOException {
        String script = Files.readString(
                projectRoot().resolve("scripts/ops/postgres-logical-restore-rehearsal.sh"),
                StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String backupVersionRead = "backup_server_version_num=$(manifest_value server_version_num)";
        String targetVersionRead = "target_server_version_num=$(\"${psql_recovery[@]}\" --command='SHOW server_version_num')";
        String backupMajor = "backup_server_major=$((backup_server_version_num / 10000))";
        String targetMajor = "target_server_major=$((target_server_version_num / 10000))";
        String majorMismatch = "if [[ \"$backup_server_major\" != \"$target_server_major\" ]]";
        String restoreWrite = "pg_restore \\\n    --host=\"$RECOVERY_PGHOST\"";

        assertTrue(script.contains(backupVersionRead),
                "restore must read the PostgreSQL server version captured by backup provenance");
        assertTrue(script.contains(targetVersionRead),
                "restore must resolve the disposable target PostgreSQL server version before writing");
        assertTrue(script.contains(backupMajor),
                "restore must derive the source PostgreSQL major version from server_version_num");
        assertTrue(script.contains(targetMajor),
                "restore must derive the target PostgreSQL major version from server_version_num");
        assertTrue(script.contains(majorMismatch),
                "the bounded rehearsal must fail closed on a cross-major restore target");
        assertTrue(script.indexOf(majorMismatch) < script.indexOf(restoreWrite),
                "major-version compatibility must be checked before pg_restore writes to the target");
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
