package com.xtrmetl.etl.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines integrity and atomic-write boundaries for PostgreSQL restore rehearsals.
 */
class PostgresLogicalRestoreAuthenticityTest {

    @Test
    void restoreRequiresAnOutOfBandExpectedArchiveDigestBeforeArchiveInspection() throws IOException {
        String script = restoreScript();

        String expectedDigestInput =
                ": \"${EXPECTED_BACKUP_SHA256:?Set EXPECTED_BACKUP_SHA256 to the independently recorded backup archive digest}\"";
        String expectedDigestValidation =
                "if [[ ! \"$EXPECTED_BACKUP_SHA256\" =~ ^[0-9a-f]{64}$ ]]";
        String manifestDigestMatch =
                "if [[ \"$expected_backup_sha256\" != \"$EXPECTED_BACKUP_SHA256\" ]]";
        String archiveInspection = "pg_restore --list \"$archive_path\"";

        assertTrue(script.contains(expectedDigestInput),
                "restore must require an expected archive digest supplied outside the mutable backup bundle");
        assertTrue(script.contains(expectedDigestValidation),
                "restore must reject malformed out-of-band digest evidence");
        assertTrue(script.contains(manifestDigestMatch),
                "the bundle manifest digest must match independently supplied digest evidence");
        assertTrue(script.indexOf(manifestDigestMatch) < script.indexOf(archiveInspection),
                "independent digest evidence must be checked before pg_restore parses the archive");
    }

    @Test
    void restoreUsesOneTransactionSoACommandFailureCannotLeavePartialApplicationState() throws IOException {
        String script = restoreScript();

        int restoreWrite = script.lastIndexOf("pg_restore \\\n");
        assertTrue(restoreWrite >= 0, "restore script must contain a database-writing pg_restore invocation");
        String restoreCommand = script.substring(restoreWrite, script.indexOf("\n\n", restoreWrite));

        assertTrue(restoreCommand.contains("--single-transaction"),
                "disposable-target restore must be atomic so any command failure rolls back the complete restore");
        assertFalse(restoreCommand.contains("--transaction-size"),
                "single-transaction recovery must not be weakened into a partially committed transaction batch");
        assertTrue(restoreCommand.contains("--exit-on-error"),
                "restore must keep explicit fail-fast semantics even though single-transaction also implies it");
    }

    private static String restoreScript() throws IOException {
        return Files.readString(
                        projectRoot().resolve("scripts/ops/postgres-logical-restore-rehearsal.sh"),
                        StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
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
