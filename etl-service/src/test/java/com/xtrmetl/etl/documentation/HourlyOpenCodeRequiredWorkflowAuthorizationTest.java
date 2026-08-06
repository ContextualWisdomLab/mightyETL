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
     * workflow without coupling the test to incidental shell-variable or log-message wording.
     *
     * @throws IOException when the production workflow cannot be read
     */
    @Test
    void waitsForEveryRequiredExactHeadWorkflow() throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

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
