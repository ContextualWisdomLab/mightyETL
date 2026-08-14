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
     * Requires the authorization loop to wait for, structurally associate, and account for every
     * workflow. The polling count and terminal diagnostic text are intentionally pinned because
     * they are part of the bounded authorization and operator-diagnostic contract.
     *
     * @throws IOException when the production workflow cannot be read
     */
    @Test
    void waitsForEveryRequiredExactHeadWorkflow() throws IOException {
        String workflow = workflow();
        String associatedRunSelection = between(
                workflow,
                "            jq -c \\\n              --arg expected_head",
                "              ' \"${all_runs_file}\" > \"${runs_file}\""
        );
        String approvalLoop = between(
                workflow,
                "            while IFS= read -r run_id; do",
                "            observed_workflow_names="
        );

        assertTrue(workflow.contains(
                "required_workflow_names='[\"CI\",\"Dependency Review\","
                        + "\"SBOM (CycloneDX)\",\"SAST Semgrep\",\"Security Scan\"]'"
        ));
        assertTrue(workflow.contains("observed_workflow_names"));
        assertTrue(workflow.contains("missing_workflow_names"));
        assertTrue(workflow.contains("for _ in $(seq 1 18); do"));
        assertTrue(
                associatedRunSelection.contains("select(.head_sha == $expected_head)"),
                "The associated-run selection must bind the exact expected head"
        );
        assertTrue(
                associatedRunSelection.contains(
                        "select(any(.pull_requests[]?; .number == $pull_request_number))"
                ),
                "The same associated-run selection must bind the exact pull-request number"
        );
        assertAppearsBefore(
                approvalLoop,
                "gh api --method POST \"/repos/${repository}/actions/runs/${run_id}/approve\"",
                "printf '%s\\n' \"${run_id}\" >> \"${approved_run_ids_file}\""
        );
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
     * Requires one marker to occur before another in the same authorization section.
     *
     * @param text authorization section being checked
     * @param first marker that must execute first
     * @param second marker that must execute later
     */
    private static void assertAppearsBefore(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue(firstIndex >= 0, () -> "Missing first marker: " + first);
        assertTrue(secondIndex > firstIndex, () -> "Marker must appear after first marker: " + second);
    }

    /**
     * Extracts a required text section between two ordered markers.
     *
     * @param text complete source text
     * @param startMarker inclusive section marker
     * @param endMarker exclusive section marker
     * @return required section text
     */
    private static String between(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        assertTrue(start >= 0, () -> "Missing start marker: " + startMarker);
        int end = text.indexOf(endMarker, start + startMarker.length());
        assertTrue(end > start, () -> "Missing end marker after start: " + endMarker);
        return text.substring(start, end);
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
