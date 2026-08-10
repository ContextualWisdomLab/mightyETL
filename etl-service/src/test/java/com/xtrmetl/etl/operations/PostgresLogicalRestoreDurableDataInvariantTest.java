package com.xtrmetl.etl.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requires PostgreSQL recovery rehearsal to verify representative persisted idempotency and durable
 * job invariants before reporting a successful restore.
 */
class PostgresLogicalRestoreDurableDataInvariantTest {

    @Test
    void restoreRejectsPersistedRowsThatViolateDurableApplicationInvariants() throws IOException {
        String script = Files.readString(
                        projectRoot().resolve("scripts/ops/postgres-logical-restore-rehearsal.sh"),
                        StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(script.contains("idempotency_integrity_violation_count="),
                "restore must explicitly evaluate persisted idempotency ledger rows");
        assertTrue(script.contains("FROM etl_idempotency_records"),
                "restore must read the restored idempotency ledger rather than infer row integrity from table existence");
        assertTrue(script.contains("idempotency_key_hash !~ '^[0-9a-f]{64}$'"),
                "restore must reject malformed persisted idempotency key hashes");
        assertTrue(script.contains("request_digest !~ '^[0-9a-f]{64}$'"),
                "restore must reject malformed persisted request digests");
        assertTrue(script.contains("Restored idempotency ledger violates mightyETL integrity invariants"),
                "restore must provide a stable operator classification for idempotency integrity failure");

        assertTrue(script.contains("durable_job_integrity_violation_count="),
                "restore must explicitly evaluate persisted durable-job rows");
        assertTrue(script.contains("FROM etl_job_records"),
                "restore must read restored durable-job state rather than infer row integrity from table existence");
        assertTrue(script.contains("job_status NOT IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')"),
                "restore must reject durable jobs outside the protected lifecycle state domain");
        assertTrue(script.contains("attempt_count < 0"),
                "restore must reject negative persisted durable-job attempt counts");
        assertTrue(script.contains("job_status IN ('PENDING', 'RUNNING') AND request_payload IS NULL"),
                "restore must reject active durable jobs whose recoverable payload is missing");
        assertTrue(script.contains("job_status IN ('SUCCEEDED', 'FAILED') AND request_payload IS NOT NULL"),
                "restore must reject terminal durable jobs that still retain request payloads");
        assertTrue(script.contains("Restored durable-job ledger violates mightyETL lifecycle invariants"),
                "restore must provide a stable operator classification for durable-job integrity failure");

        String relationCheck = "required_application_relation_count=";
        String idempotencyCheck = "idempotency_integrity_violation_count=";
        String durableJobCheck = "durable_job_integrity_violation_count=";
        String success = "PostgreSQL restore rehearsal completed on the explicit disposable target";
        assertTrue(script.indexOf(relationCheck) < script.indexOf(idempotencyCheck),
                "row-level invariants must run only after required application relations are proven present");
        assertTrue(script.indexOf(idempotencyCheck) < script.indexOf(durableJobCheck),
                "idempotency and durable-job invariants must run in a deterministic order");
        assertTrue(script.indexOf(durableJobCheck) < script.indexOf(success),
                "restore must not report success before durable row invariants are verified");
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
