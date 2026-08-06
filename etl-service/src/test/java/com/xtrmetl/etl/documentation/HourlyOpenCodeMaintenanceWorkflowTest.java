package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the credential, authority, supply-chain, publication, and exact-head validation
 * boundaries of the scheduled OpenCode maintenance workflow.
 *
 * <p>The model may edit and push one bounded feature branch. It never receives pull-request write
 * authority. A deterministic non-checkout publisher may create one draft pull request, while a
 * second isolated non-checkout job may authorize only approval-required workflow runs associated
 * with that exact pull request and exact head. These tests keep those authorities physically
 * separated from model and repository-code execution.</p>
 */
class HourlyOpenCodeMaintenanceWorkflowTest {

    private static final Pattern SECRET_REFERENCE = Pattern.compile(
            "\\$\\{\\{\\s*secrets\\.([A-Z0-9_]+)\\s*}}"
    );

    private static String workflow;

    /**
     * Reads the workflow once and normalizes line endings for deterministic cross-platform tests.
     *
     * @throws IOException when the workflow exists but cannot be read as UTF-8 text
     */
    @BeforeAll
    static void readWorkflow() throws IOException {
        Path workflowPath = projectRoot().resolve(
                ".github/workflows/hourly-opencode-maintenance.yml"
        );
        assertTrue(Files.exists(workflowPath), "The hourly OpenCode workflow must exist");
        workflow = Files.readString(workflowPath, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    /** Verifies one serialized, bounded run every hour. */
    @Test
    void schedulesOneBoundedNonOverlappingRunPerHour() {
        assertTrue(workflow.contains("cron: \"43 * * * *\""));
        assertTrue(workflow.contains("group: hourly-opencode-maintenance"));
        assertTrue(workflow.contains("cancel-in-progress: false"));
        assertTrue(workflow.contains("timeout-minutes: 50"));
        assertTrue(workflow.contains(
                "timeout --signal=TERM --kill-after=30s 45m opencode run"
        ));
    }

    /** Verifies immutable checkout and OpenCode installation without persisted credentials. */
    @Test
    void pinsCheckoutAndOpenCodeWithoutPersistedCredentials() {
        assertTrue(workflow.contains(
                "actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0"
        ));
        assertTrue(workflow.contains("fetch-depth: 1"));
        assertTrue(workflow.contains("persist-credentials: false"));
        assertTrue(workflow.contains(
                "https://github.com/anomalyco/opencode/releases/download/"
                        + "v${OPENCODE_VERSION}/opencode-linux-x64.tar.gz"
        ));
        assertTrue(workflow.contains(
                "OPENCODE_SHA256: \"8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937\""
        ));
        assertTrue(workflow.contains("sha256sum --check --strict"));
        assertTrue(workflow.contains("tar --extract --gzip"));
        assertTrue(workflow.contains(
                "test \"$(\"${install_dir}/opencode\" --version)\" = \"${OPENCODE_VERSION}\""
        ));
        assertFalse(workflow.contains("npm install"));
        assertFalse(workflow.contains("opencode-ai@latest"));
        assertFalse(workflow.contains("anomalyco/opencode/github@"));
    }

    /** Verifies ephemeral Git credentials for branch pushes and deterministic cleanup. */
    @Test
    void bootstrapsAndRemovesDirectTokenGitCredentials() {
        assertTrue(workflow.contains("GH_TOKEN: ${{ github.token }}"));
        assertTrue(workflow.contains(
                "git_credential_key=\"credential.https://github.com.helper\""
        ));
        assertTrue(workflow.contains("cleanup_git_credentials()"));
        assertTrue(workflow.contains("trap cleanup_git_credentials EXIT"));
        assertTrue(workflow.contains(
                "git config --local --add \"${git_credential_key}\" \"\""
        ));
        assertTrue(workflow.contains(
                "git config --local --add \"${git_credential_key}\" "
                        + "\"!gh auth git-credential\""
        ));
        assertTrue(workflow.contains(
                "git config --local user.name \"opencode-agent[bot]\""
        ));
        assertTrue(workflow.contains(
                "git config --local user.email "
                        + "\"opencode-agent[bot]@users.noreply.github.com\""
        ));
        assertFalse(workflow.contains("AUTHORIZATION: basic"));
    }

    /** Verifies NVIDIA NIM is the sole model credential and plain OpenCode owns no PR lifecycle. */
    @Test
    void usesOnlyNvidiaNimWithPlainOpenCodeRun() {
        assertEquals(Set.of("NVIDIA_NIM_API_KEY"), referencedSecrets());
        assertTrue(workflow.contains(
                "NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}"
        ));
        assertTrue(workflow.contains("MODEL: nvidia/deepseek-ai/deepseek-v4-pro"));
        assertTrue(workflow.contains("opencode run --model \"${MODEL}\" --auto"));
        assertFalse(workflow.contains("opencode github run"));
        assertFalse(workflow.contains("USE_GITHUB_TOKEN"));
        assertFalse(workflow.toLowerCase(Locale.ROOT).contains("copilot"));
        assertFalse(workflow.contains("ANTHROPIC_API_KEY"));
        assertFalse(workflow.contains("OPENAI_API_KEY"));
    }

    /**
     * Proves the model has read-only pull-request authority and both PR-writing jobs are
     * deterministic non-checkout jobs without the NVIDIA credential.
     */
    @Test
    void isolatesPullRequestAndActionsWriteAuthorityFromTheAgent() {
        String maintenance = maintenanceJob();
        String publisher = publicationJob();
        String authorizer = authorizationJob();

        assertTrue(workflow.contains("permissions:\n  contents: read\n\njobs:"));
        assertTrue(maintenance.contains("actions: read"));
        assertTrue(maintenance.contains("contents: write"));
        assertTrue(maintenance.contains("pull-requests: read"));
        assertFalse(maintenance.contains("pull-requests: write"));
        assertFalse(maintenance.contains("actions: write"));

        assertTrue(publisher.contains("pull-requests: write"));
        assertTrue(publisher.contains("contents: read"));
        assertFalse(publisher.contains("actions/checkout@"));
        assertFalse(publisher.contains("NVIDIA_API_KEY"));
        assertFalse(publisher.contains("/reviews"));
        assertFalse(publisher.contains("/merge"));

        assertTrue(authorizer.contains("actions: write"));
        assertTrue(authorizer.contains("pull-requests: read"));
        assertFalse(authorizer.contains("actions/checkout@"));
        assertFalse(authorizer.contains("NVIDIA_API_KEY"));
        assertFalse(authorizer.contains("pull-requests: write"));

        assertEquals(1, countOccurrences(workflow, "actions: write"));
        assertEquals(1, countOccurrences(workflow, "contents: write"));
        assertEquals(1, countOccurrences(workflow, "pull-requests: write"));
        assertFalse(workflow.contains("id-token:"));
        assertFalse(workflow.contains("security-events: write"));
    }

    /** Verifies one strict branch or existing PR is selected before deterministic publication. */
    @Test
    void publishesOnlyOneValidatedAgentCandidate() {
        assertTrue(workflow.contains(
                "automation_branch_heads_before: "
                        + "${{ steps.snapshot_heads.outputs.automation_branch_heads }}"
        ));
        assertTrue(workflow.contains(
                "agent_candidate: ${{ steps.detect_candidate.outputs.agent_candidate }}"
        ));
        assertTrue(workflow.contains("automation/opencode-"));
        assertTrue(workflow.contains("Multiple agent publication candidates were detected"));
        assertTrue(workflow.contains("kind: \"existing_pr\""));
        assertTrue(workflow.contains("kind: \"new_branch\""));
        assertTrue(workflow.contains("draft: true"));
        assertTrue(workflow.contains("startswith(\".github/\")"));
        assertTrue(workflow.contains("CODEOWNERS"));
        assertTrue(workflow.contains("Agent branch is not ahead of develop"));
    }

    /**
     * Requires every workflow-run decision to bind the event, exact SHA, and associated pull
     * request number before the isolated job can authorize a waiting run.
     */
    @Test
    void authorizesOnlyRunsAssociatedWithTheExactPullRequestHead() {
        assertTrue(workflow.contains("required_workflow_names="));
        assertTrue(workflow.contains("event=pull_request"));
        assertTrue(workflow.contains("head_sha=${expected_head}"));
        assertTrue(workflow.contains("--argjson pull_request_number \"${number}\""));
        assertTrue(workflow.contains(
                "any(.pull_requests[]?; .number == $pull_request_number)"
        ));
        assertTrue(workflow.contains(".head_sha == $expected_head"));
        assertTrue(workflow.contains("/actions/runs/${run_id}/approve"));
        assertTrue(workflow.contains("Missing required exact-head workflows"));
        assertTrue(workflow.contains("PR #${number} moved"));
        assertFalse(workflow.contains("gh pr review --approve"));
        assertFalse(workflow.contains("/pulls/${number}/merge"));
    }

    /** Verifies the prompt itself mirrors the hard authority boundary and bounded branch contract. */
    @Test
    void promptForbidsPullRequestMutationMergeAndProtectedBranchPushes() {
        assertTrue(workflow.contains("Start every run by inspecting every open pull request"));
        assertTrue(workflow.contains("exact current head"));
        assertTrue(workflow.contains(
                "Do not create, update, approve, close, or merge a pull request directly"
        ));
        assertTrue(workflow.contains("Never push directly to develop or main"));
        assertTrue(workflow.contains("exactly one automation/opencode-"));
        assertTrue(workflow.contains("Do not bypass branch protection"));
        assertTrue(workflow.contains("Do not alter the existing review agent"));
        assertTrue(workflow.contains("Do not change any review-agent secret name"));
        assertTrue(workflow.contains("Do not modify .github/workflows/"));
        assertTrue(workflow.contains("automation-maintenance"));
        assertTrue(workflow.contains("Do not print, echo, summarize, or expose secret values"));
    }

    /** @return workflow text for the OpenCode execution job only */
    private static String maintenanceJob() {
        return jobSection("  maintain-repository:", "  publish-agent-pull-request:");
    }

    /** @return workflow text for the deterministic draft-PR publisher only */
    private static String publicationJob() {
        return jobSection("  publish-agent-pull-request:", "  authorize-exact-head-checks:");
    }

    /** @return workflow text for the exact-head workflow-run authorizer */
    private static String authorizationJob() {
        int start = workflow.indexOf("  authorize-exact-head-checks:");
        assertTrue(start >= 0, "The isolated exact-head authorization job must exist");
        return workflow.substring(start);
    }

    /**
     * Extracts one job section between two top-level job keys.
     *
     * @param startMarker first job marker
     * @param endMarker following job marker
     * @return exact workflow section
     */
    private static String jobSection(String startMarker, String endMarker) {
        int start = workflow.indexOf(startMarker);
        int end = workflow.indexOf(endMarker);
        assertTrue(start >= 0, "Missing workflow job: " + startMarker);
        assertTrue(end > start, "Invalid workflow job order for: " + startMarker);
        return workflow.substring(start, end);
    }

    /**
     * Counts non-overlapping literal occurrences.
     *
     * @param text complete text
     * @param fragment non-empty literal fragment
     * @return occurrence count
     */
    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int cursor = 0;
        while ((cursor = text.indexOf(fragment, cursor)) >= 0) {
            count++;
            cursor += fragment.length();
        }
        return count;
    }

    /** @return immutable set of referenced repository-secret names */
    private static Set<String> referencedSecrets() {
        Matcher matcher = SECRET_REFERENCE.matcher(workflow);
        Set<String> secretNames = new java.util.HashSet<>();
        while (matcher.find()) {
            secretNames.add(matcher.group(1));
        }
        return Set.copyOf(secretNames);
    }

    /**
     * Finds the repository root from either reactor-root or module-local execution.
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
