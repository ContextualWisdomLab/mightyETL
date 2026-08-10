package com.xtrmetl.etl.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binds PostgreSQL restore rehearsal success to the minimum application relations owned by the
 * current protected mightyETL database contract.
 */
class PostgresLogicalRestoreApplicationInvariantTest {

    @Test
    void restoreVerifiesCriticalApplicationRelationsBeforeReportingSuccess() throws IOException {
        String script = Files.readString(
                        projectRoot().resolve("scripts/ops/postgres-logical-restore-rehearsal.sh"),
                        StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String requiredRelationsQuery =
                "SELECT count(*) FROM (VALUES (to_regclass('public.processed_data')), "
                        + "(to_regclass('public.etl_idempotency_records')), "
                        + "(to_regclass('public.etl_job_records'))) AS required(relation_oid) "
                        + "WHERE relation_oid IS NOT NULL";
        String invariantFailure =
                "Restored database is missing one or more required mightyETL application relations";
        String flywayVerification =
                "if [[ \"$restored_flyway_schema_version\" != \"$expected_flyway_schema_version\" ]]";
        String success =
                "PostgreSQL restore rehearsal completed on the explicit disposable target";

        assertTrue(script.contains(requiredRelationsQuery),
                "restore must verify the current processed-data, idempotency, and durable-job relations");
        assertTrue(script.contains("required_application_relation_count"),
                "restore must bind the relation check to an explicit finite result");
        assertTrue(script.contains("!= \"3\""),
                "restore must fail closed unless all three required relations exist");
        assertTrue(script.contains(invariantFailure),
                "restore must emit a stable operator classification when application relations are missing");
        assertTrue(script.indexOf(flywayVerification) < script.indexOf(requiredRelationsQuery),
                "application relation verification must occur after Flyway provenance verification");
        assertTrue(script.indexOf(requiredRelationsQuery) < script.indexOf(success),
                "restore must not report success before application relations are verified");
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
