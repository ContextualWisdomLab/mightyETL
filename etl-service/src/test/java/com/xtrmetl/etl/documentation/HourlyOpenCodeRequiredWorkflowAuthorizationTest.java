package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards complete exact-head workflow-run materialization after an OpenCode branch update.
 *
 * <p>GitHub can create approval-required pull-request runs asynchronously. Stopping after the first
 * run appears can leave later CI or security workflows waiting forever. The isolated authorization
 * job must therefore continue discovering and authorizing runs until every named pull-request
 * workflow required by mightyETL has materialized for the unchanged exact head.</p>
 */
class HourlyOpenCodeRequiredWorkflowAuthorizationTest {

    /**
     * Requires the authorization loop to wait for, associate, and account for every required
     * workflow. The polling count and terminal diagnostic text are intentionally pinned because
     * they are part of the bounded authorization and operator-diagnostic contract.
     *
     * @throws IOException when the production workflow cannot be read
     */
    @Test
    void waitsForEveryRequiredExactHeadWorkflow() throws IOException {
        String workflow = workflow();

        assertTrue(workflow.contains(
                "required_workflow_names='[\"CI\",\"Dependency Review\","
                        + "\"SBOM (CycloneDX)\",\"SAST Semgrep\",\"Security Scan\"]'"
        ));
        assertTrue(workflow.contains("observed_workflow_names"));
        assertTrue(workflow.contains("missing_workflow_names"));
        assertTrue(workflow.contains("for _ in $(seq 1 18); do"));
        assertTrue(workflow.contains(".head_sha == $expected_head"));
        assertTrue(workflow.contains(
                "any(.pull_requests[]?; .number == $pull_request_number)"
        ));
        assertTrue(workflow.contains("/actions/runs/${run_id}/approve"));
        assertTrue(workflow.contains("jq 'length' <<<\"${missing_workflow_names}\""));
        assertTrue(workflow.contains("Missing required exact-head workflows for PR"));
        assertTrue(workflow.contains("Authorized exact-head pull-request checks for PR"));
    }

    /**
     * Requires successful approvals to be remembered across polling passes so eventual-consistency
     * lag cannot make the authorizer POST the same run twice and fail under {@code set -e}.
     *
     * @throws IOException when the production workflow cannot be read
     */
    @Test
    void doesNotApproveTheSameWorkflowRunTwiceAcrossPollingPasses() throws IOException {
        String workflow = workflow();

        assertTrue(
                workflow.contains(
                        "approved_run_ids_file=\"${RUNNER_TEMP}/pr-${number}-approved-run-ids.txt\""
                ),
                "Authorization must keep one approved-run ledger for the overall polling operation"
        );
        assertTrue(
                workflow.contains(": > \"${approved_run_ids_file}\""),
                "The approved-run ledger must be initialized once before polling begins"
        );
        assertTrue(
                workflow.contains(
                        "if grep -Fxq \"${run_id}\" \"${approved_run_ids_file}\"; then"
                ),
                "Polling must skip a run id that was already approved on an earlier pass"
        );
        assertTrue(
                workflow.contains(
                        "printf '%s\\n' \"${run_id}\" >> \"${approved_run_ids_file}\""
                ),
                "A run id must be recorded only after its approval request succeeds"
        );
    }

    /**
     * Reads the production workflow with normalized line endings.
     *
     * @return UTF-8 workflow source
     * @throws IOException when the production workflow cannot be read
     */
    private static String workflow() throws IOException {
        return Files.readString(
                projectRoot().resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }

    /**
     * Finds the repository root from root- or module-scoped Maven execution.
     *
     * @return absolute repository root
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