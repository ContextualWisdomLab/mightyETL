package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the durable cancellation API, race contract, rollout, rollback, and changelog aligned.
 */
class DurableJobCancellationDocumentationTest {

    @Test
    void operationsRunbookDocumentsTheAuthoritativeCancellationContract() throws IOException {
        String runbook = read("docs/operations/durable-job-cancellation.md")
                .replaceAll("\\s+", " ");

        assertTrue(runbook.contains("POST /api/etl/jobs/{job_record_id}/cancellation"));
        assertTrue(runbook.contains("Idempotency-Replayed: false"));
        assertTrue(runbook.contains("Idempotency-Replayed: true"));
        assertTrue(runbook.contains("etl_job_cancellation_key_reused"));
        assertTrue(runbook.contains("etl_job_already_succeeded"));
        assertTrue(runbook.contains("etl_job_already_failed"));
        assertTrue(runbook.contains("Cancellation commits first"));
        assertTrue(runbook.contains("Success commits first"));
        assertTrue(runbook.contains("StaleEtlJobLeaseException"));
        assertTrue(runbook.contains("rolls back its target and etl_idempotency_records writes"));
        assertTrue(runbook.contains("V6__add_etl_job_cancellation.sql"));
        assertTrue(runbook.contains("mightyetl.etl.jobs.intake-enabled=false"));
        assertTrue(runbook.contains("does not claim arbitrary external side-effect reversal"));
        assertTrue(runbook.contains(
                "Never silently map `CANCELLED` to `FAILED` or `SUCCEEDED`"
        ));
        assertTrue(runbook.contains("RFC 9110"));
        assertTrue(runbook.contains("RFC 9457"));
        assertTrue(runbook.contains("PostgreSQL Global Development Group. (2026)"));
    }

    @Test
    void designAndPlanKeepDatabaseAuthorityAndVerificationExplicit() throws IOException {
        String design = read(
                "docs/superpowers/specs/2026-08-06-durable-job-cancellation-design.md"
        ).replaceAll("\\s+", " ");
        String plan = read(
                "docs/superpowers/plans/2026-08-06-durable-job-cancellation.md"
        ).replaceAll("\\s+", " ");

        assertTrue(design.contains("One conditional UPDATE is the cancellation authority"));
        assertTrue(design.contains("Exactly one terminal outcome may win"));
        assertTrue(design.contains("same-key replay"));
        assertTrue(design.contains("transactional target effects"));
        assertTrue(plan.contains("Added production statement and branch coverage remains 100%"));
        assertTrue(plan.contains("No project test may be skipped"));
        assertTrue(plan.contains("Run all verification"));
    }

    @Test
    void changelogRecordsTheBuyerVisibleCancellationSlice() throws IOException {
        String changelog = read("CHANGELOG.md").replaceAll("\\s+", " ");

        assertTrue(changelog.contains("owner-scoped durable-job cancellation"));
        assertTrue(changelog.contains("CANCELLED"));
        assertTrue(changelog.contains("cancellation_key_hash"));
        assertTrue(changelog.contains("cancellation-first"));
        assertTrue(changelog.contains("transactional target and response-ledger effects"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * Finds the reactor root from repository-root or module-scoped Maven execution.
     *
     * @return repository root containing the Maven reactor
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
