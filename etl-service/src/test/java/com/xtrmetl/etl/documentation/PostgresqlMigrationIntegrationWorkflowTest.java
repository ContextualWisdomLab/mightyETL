package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the real PostgreSQL migration gate aligned with replay-lineage safety requirements.
 */
class PostgresqlMigrationIntegrationWorkflowTest {

    @Test
    void workflowUsesLeastPrivilegeExactHeadCheckoutAndPostgresqlEighteen() throws IOException {
        String workflow = read(".github/workflows/postgresql-migration-integration.yml");

        assertTrue(workflow.contains("name: PostgreSQL Migration Integration"));
        assertTrue(workflow.contains("branches:\n      - develop"));
        assertTrue(workflow.contains("permissions:\n  contents: read"));
        assertTrue(workflow.contains("timeout-minutes: 15"));
        assertTrue(workflow.contains("image: postgres:18-alpine"));
        assertTrue(workflow.contains(
                "if: github.event_name != 'workflow_dispatch' || "
                        + "github.ref_name == github.event.repository.default_branch"
        ));
        assertTrue(workflow.contains(
                "uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0"
        ));
        assertTrue(workflow.contains(
                "repository: ${{ github.event_name == 'pull_request' "
                        + "&& github.event.pull_request.head.repo.full_name || github.repository }}"
        ));
        assertTrue(workflow.contains(
                "ref: ${{ github.event_name == 'pull_request' "
                        + "&& github.event.pull_request.head.sha "
                        + "|| github.event.repository.default_branch }}"
        ));
        assertTrue(workflow.contains("persist-credentials: false"));
        assertTrue(workflow.contains("bash scripts/verify-postgresql-migrations.sh"));
        assertFalse(workflow.contains("refs/pull/"));
        assertFalse(workflow.contains("github.event.pull_request.merge_commit_sha"));
        assertFalse(workflow.contains("pull_request_target:"));
        assertFalse(workflow.contains("COPILOT_GITHUB_TOKEN"));
        assertFalse(workflow.contains("NVIDIA_NIM_API_KEY"));
        assertFalse(workflow.contains("contents: write"));
    }

    @Test
    void verificationScriptAppliesEveryMigrationAndChecksReplayConstraints() throws IOException {
        String script = read("scripts/verify-postgresql-migrations.sh");

        assertTrue(script.contains("set -Eeuo pipefail"));
        assertTrue(script.contains("pg_isready"));
        assertTrue(script.contains("sort -zV"));
        assertTrue(script.contains("--set ON_ERROR_STOP=1"));
        assertTrue(script.contains("replay_source_job_record_id"));
        assertTrue(script.contains("replay_root_job_record_id"));
        assertTrue(script.contains("replay_generation_count"));
        assertTrue(script.contains("constraint_record.confdeltype = 'r'"));
        assertTrue(script.contains("replay_check_definition NOT ILIKE '%100%'"));
        assertTrue(script.contains("cancellation_key_hash"));
        assertTrue(script.contains("job_cancelled_at"));
        assertTrue(script.contains("pg_dump --schema-only --no-owner --no-privileges"));
        assertFalse(script.contains("set +e"));
        assertFalse(script.contains("|| true"));
    }

    /**
     * Reads one repository contract with platform-independent line endings.
     *
     * <p>Git may materialize text files with CRLF on Windows runners. Normalizing both CRLF
     * and lone carriage returns keeps semantic workflow assertions identical across the CI
     * operating-system matrix without weakening their exact content requirements.</p>
     *
     * @param relativePath repository-relative file path
     * @return UTF-8 content using LF line endings
     * @throws IOException when the repository contract cannot be read
     */
    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    /**
     * Finds the Maven reactor root from repository-root or module-local execution.
     *
     * @return repository root containing the workflow and migration verifier
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
