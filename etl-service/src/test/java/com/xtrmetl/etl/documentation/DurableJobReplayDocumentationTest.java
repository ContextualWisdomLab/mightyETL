package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps replay source immutability, payload proof, lineage, compatibility, and operations aligned.
 */
class DurableJobReplayDocumentationTest {

    @Test
    void operationsRunbookDocumentsAdmissionLineageAndRollback() throws IOException {
        String runbook = read("docs/operations/durable-job-replay.md")
                .replaceAll("\\s+", " ");

        assertTrue(runbook.contains("POST /api/etl/jobs/{source_job_record_id}/replays"));
        assertTrue(runbook.contains("Replay never changes a terminal source back to `PENDING`"));
        assertTrue(runbook.contains("Idempotency-Replayed: false"));
        assertTrue(runbook.contains("Idempotency-Replayed: true"));
        assertTrue(runbook.contains("etl_job_replay_payload_mismatch"));
        assertTrue(runbook.contains("etl_job_replay_key_reused"));
        assertTrue(runbook.contains("replay_source_job_record_id"));
        assertTrue(runbook.contains("replay_root_job_record_id"));
        assertTrue(runbook.contains("replay_generation_count"));
        assertTrue(runbook.contains("ON DELETE RESTRICT"));
        assertTrue(runbook.contains("generation 1 through 100"));
        assertTrue(runbook.contains("prov:wasDerivedFrom"));
        assertTrue(runbook.contains("Do not drop V7 while replay rows exist"));
        assertTrue(runbook.contains("does not prove that replaying a connector"));
        assertTrue(runbook.contains("RFC 9110"));
        assertTrue(runbook.contains("RFC 9457"));
        assertTrue(runbook.contains("PROV-O"));
    }

    @Test
    void designAndPlanPreserveTheSingleExecutionEngine() throws IOException {
        String design = read(
                "docs/superpowers/specs/2026-08-06-durable-job-replay-design.md"
        ).replaceAll("\\s+", " ");
        String plan = read(
                "docs/superpowers/plans/2026-08-06-durable-job-replay.md"
        ).replaceAll("\\s+", " ");

        assertTrue(design.contains("The source is never updated"));
        assertTrue(design.contains("Only `FAILED` and `CANCELLED`"));
        assertTrue(design.contains("ordinary `PENDING` job"));
        assertTrue(design.contains("No replay-specific worker or scheduler exists"));
        assertTrue(design.contains("replay_generation_count"));
        assertTrue(plan.contains("Never update a terminal source back to `PENDING`"));
        assertTrue(plan.contains("Run all verification"));
        assertTrue(plan.contains("no project test is skipped"));
    }

    @Test
    void doctoringPinsVersionedReplayAndLockDomains() throws IOException {
        String evidence = read(
                "docs/doctoring/durable-job-replay-key-domain-separation.md"
        ).replaceAll("\\s+", " ");

        assertTrue(evidence.contains("mightyetl:durable-job-replay:v1:"));
        assertTrue(evidence.contains("mightyetl:durable-job-replay-lock:v1:"));
        assertTrue(evidence.contains("isolated from ordinary submission-key hashing"));
        assertTrue(evidence.contains("exact strings are persisted behavior"));
        assertTrue(evidence.contains("does not claim cSHAKE"));
        assertTrue(evidence.contains("NIST Special Publication 800-185"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /** @return repository root from reactor-root or module-local execution */
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
