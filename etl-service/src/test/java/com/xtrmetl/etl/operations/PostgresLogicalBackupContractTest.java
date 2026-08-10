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
 * Defines the first executable recovery contract for a local PostgreSQL logical backup.
 */
class PostgresLogicalBackupContractTest {

    @Test
    void backupToolCreatesAPrivateVerifiedCustomArchiveWithProvenance() throws IOException {
        Path scriptPath = projectRoot().resolve("scripts/ops/postgres-logical-backup.sh");
        assertTrue(
                Files.isRegularFile(scriptPath),
                "A supported logical-backup command must exist before named volumes can be called recoverable"
        );

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(script.contains("set -euo pipefail"), "backup failures must fail closed");
        assertTrue(script.contains("umask 077"), "backup artifacts must be private by default");
        assertTrue(script.contains("${BACKUP_DIRECTORY:?"), "operators must choose the backup destination explicitly");
        assertTrue(script.contains("${APPLICATION_SOURCE_SHA:?"), "backup provenance must bind the exact application source");
        assertTrue(script.contains("pg_dump"), "the local PostgreSQL profile must use a database-consistent logical dump");
        assertTrue(script.contains("--format=custom"), "the archive must use PostgreSQL custom format for pg_restore validation");
        assertTrue(script.contains("pg_restore --list"), "the completed archive must be structurally verified before publication");
        assertTrue(script.contains("server_version_num"), "the manifest must record the PostgreSQL server version");
        assertTrue(script.contains("flyway_schema_history"), "the manifest must bind the Flyway migration level");
        assertTrue(script.contains("backup_sha256"), "the manifest must bind an integrity digest");
        assertTrue(script.contains("application_source_sha"), "the manifest must record exact source identity");
        assertTrue(script.contains("mv --"), "temporary backup artifacts must be atomically published only after verification");
        assertFalse(script.contains("docker cp /var/lib/postgresql/data"), "copying a live PostgreSQL data directory is not backup");
        assertFalse(script.contains("echo \"$PGPASSWORD\""), "database credentials must never be printed");
    }

    @Test
    void recoveryRunbookSeparatesVerifiedBackupFromUnprovenRestoreAndRecoveryObjectives() throws IOException {
        Path runbookPath = projectRoot().resolve("docs/ops/postgres-recovery.md");
        assertTrue(Files.isRegularFile(runbookPath), "the executable backup boundary needs an operator recovery runbook");

        String runbook = Files.readString(runbookPath, StandardCharsets.UTF_8);
        assertTrue(runbook.contains("APPLICATION_SOURCE_SHA"), "operators need exact application-source provenance");
        assertTrue(runbook.contains("pg_restore --list"), "runbook must explain archive structural verification");
        assertTrue(runbook.contains("flyway_schema_history"), "runbook must explain migration-level provenance");
        assertTrue(runbook.contains("Backup is not restore"), "a produced archive must not be represented as restore proof");
        assertTrue(runbook.contains("RPO: not measured"), "RPO must remain evidence-based rather than invented");
        assertTrue(runbook.contains("RTO: not measured"), "RTO must remain evidence-based rather than invented");
        assertTrue(runbook.contains("Kafka"), "database recovery scope must distinguish Kafka side effects");
        assertTrue(runbook.contains("Debezium"), "database recovery scope must distinguish Debezium state");
        assertTrue(runbook.contains("DLT"), "database recovery scope must distinguish dead-letter state");
        assertTrue(runbook.contains("external target"), "database recovery scope must distinguish external target effects");
    }

    @Test
    void changelogRecordsTheNewBackupCapabilityWithoutClaimingRestoreReadiness() throws IOException {
        String changelog = Files.readString(projectRoot().resolve("CHANGELOG.md"), StandardCharsets.UTF_8);
        assertTrue(
                changelog.contains("verified PostgreSQL logical backup"),
                "the operator-visible backup capability must be discoverable in the changelog"
        );
        assertTrue(
                changelog.contains("does not prove restore or disaster-recovery readiness"),
                "the changelog must not promote a backup artifact into an untested recovery claim"
        );
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
