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
 * Defines executable recovery contracts for PostgreSQL backup and disposable-target restore rehearsal.
 */
class PostgresLogicalBackupContractTest {

    @Test
    void backupToolCreatesAPrivateVerifiedCustomArchiveWithProvenance() throws IOException {
        Path scriptPath = projectRoot().resolve("scripts/ops/postgres-logical-backup.sh");
        assertTrue(Files.isRegularFile(scriptPath), "a supported logical-backup command must exist");

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(script.contains("set -euo pipefail"), "backup failures must fail closed");
        assertTrue(script.contains("umask 077"), "backup artifacts must be private by default");
        assertTrue(script.contains("${BACKUP_DIRECTORY:?"), "operators must choose the backup destination explicitly");
        assertTrue(script.contains("${APPLICATION_SOURCE_SHA:?"), "backup provenance must bind the exact application source");
        assertTrue(script.contains("pg_dump"), "the local PostgreSQL profile must use a database-consistent logical dump");
        assertTrue(script.contains("--format=custom"), "the archive must use PostgreSQL custom format");
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
    void backupToolReservesTheFinalIdentityBeforeWritingToPreventSameSecondCollisions() throws IOException {
        String script = Files.readString(projectRoot().resolve("scripts/ops/postgres-logical-backup.sh"), StandardCharsets.UTF_8);
        String reservation = "reservation_directory=\"${backup_directory}/.${backup_identity}.reservation\"";
        String atomicReservation = "if ! mkdir -- \"$reservation_directory\"";
        String temporaryBundle = "temporary_bundle=$(mktemp -d";

        assertTrue(script.contains(reservation), "a timestamp/source backup identity needs a same-filesystem reservation");
        assertTrue(script.contains(atomicReservation), "concurrent creation of the same identity must fail closed");
        assertTrue(script.contains("rm -rf -- \"$reservation_directory\""), "the reservation must be released on exit");
        assertTrue(script.indexOf(atomicReservation) < script.indexOf(temporaryBundle), "reserve identity before backup work starts");
    }

    @Test
    void restoreRehearsalVerifiesProvenanceAndRequiresAnEmptyExplicitTarget() throws IOException {
        Path scriptPath = projectRoot().resolve("scripts/ops/postgres-logical-restore-rehearsal.sh");
        assertTrue(Files.isRegularFile(scriptPath), "a verified backup needs a bounded disposable-target restore rehearsal command");

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(script.contains("set -euo pipefail"), "restore rehearsal failures must fail closed");
        assertTrue(script.contains("${BACKUP_BUNDLE:?"), "the archive bundle must be explicit");
        assertTrue(script.contains("${EXPECTED_APPLICATION_SOURCE_SHA:?"), "restore must bind the expected application source");
        assertTrue(script.contains("${RECOVERY_PGHOST:?"), "restore target host must be explicit");
        assertTrue(script.contains("${RECOVERY_PGDATABASE:?"), "restore target database must be explicit");
        assertTrue(script.contains("backup_sha256"), "restore must verify the manifest digest");
        assertTrue(script.contains("application_source_sha"), "restore must verify application-source provenance");
        assertTrue(script.contains("pg_restore --list"), "archive structure must be verified before restore");
        assertTrue(script.contains("user_table_count"), "restore must prove the target is empty before writing");
        assertTrue(script.contains("pg_restore"), "rehearsal must use PostgreSQL restore tooling");
        assertTrue(script.contains("--exit-on-error"), "restore must fail at the first PostgreSQL restore error");
        assertTrue(script.contains("--no-owner"), "rehearsal must not require archived ownership to exist");
        assertTrue(script.contains("--no-privileges"), "rehearsal must not import archived grants into the target");
        assertTrue(script.contains("restored_flyway_schema_version"), "post-restore migration identity must be verified");
        assertFalse(script.contains("source \"$manifest_path\""), "an untrusted manifest must never be executed as shell code");
        assertFalse(script.contains("--clean"), "the rehearsal must not make an arbitrary target destructive");
        assertFalse(script.contains("dropdb"), "the rehearsal must not drop a database");
        assertFalse(script.contains("createdb"), "the operator must provision the disposable target explicitly");
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
        assertTrue(changelog.contains("verified PostgreSQL logical backup"), "the backup capability must be discoverable");
        assertTrue(changelog.contains("does not prove restore or disaster-recovery readiness"), "backup must not inflate readiness");
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
