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
 * Guards the credential, authority, and supply-chain boundaries of the scheduled OpenCode
 * maintenance workflow.
 *
 * <p>The scheduled agent is allowed to prepare feature-branch pull requests. It is not a
 * reviewer or merger. These tests make that separation visible to beginners and prevent a
 * later workflow edit from silently adding a fallback provider, mutable tool version, elevated
 * token permission, protected-branch push, or self-approval path.</p>
 */
class HourlyOpenCodeMaintenanceWorkflowTest {

    private static final Pattern SECRET_REFERENCE = Pattern.compile(
            "\\$\\{\\{\\s*secrets\\.([A-Z0-9_]+)\\s*}}"
    );

    private static String workflow;

    /**
     * Reads the workflow once after first producing an ordinary assertion failure when the
     * production workflow has not yet been implemented.
     *
     * @throws IOException when the workflow exists but cannot be read as UTF-8 text
     */
    @BeforeAll
    static void readWorkflow() throws IOException {
        Path workflowPath = projectRoot().resolve(
                ".github/workflows/hourly-opencode-maintenance.yml"
        );
        assertTrue(
                Files.exists(workflowPath),
                "The hourly OpenCode maintenance workflow must exist"
        );
        workflow = Files.readString(workflowPath, StandardCharsets.UTF_8);
    }

    /**
     * Verifies that runs are offset from the top of the hour, serialized, and time bounded,
     * including forced termination when an agent ignores the graceful termination signal.
     */
    @Test
    void schedulesOneBoundedNonOverlappingRunPerHour() {
        assertTrue(workflow.contains("cron: \"43 * * * *\""));
        assertTrue(workflow.contains("group: hourly-opencode-maintenance"));
        assertTrue(workflow.contains("cancel-in-progress: false"));
        assertTrue(workflow.contains("timeout-minutes: 50"));
        assertTrue(workflow.contains(
                "timeout --signal=TERM --kill-after=30s 45m opencode github run"
        ));
    }

    /**
     * Verifies that repository source and the OpenCode executable are pinned without retaining
     * checkout credentials that a generated process could reuse implicitly.
     */
    @Test
    void pinsCheckoutAndOpenCodeWithoutPersistedCredentials() {
        assertTrue(workflow.contains(
                "actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0"
        ));
        assertTrue(workflow.contains("fetch-depth: 1"));
        assertTrue(workflow.contains("persist-credentials: false"));
        assertTrue(workflow.contains("npm install --global opencode-ai@1.18.13"));
        assertTrue(workflow.contains("test \"$(opencode --version)\" = \"1.18.13\""));
        assertFalse(workflow.contains("opencode-ai@latest"));
        assertFalse(workflow.contains("anomalyco/opencode/github@"));
    }

    /**
     * Verifies that direct-token mode can actually create commits and push a feature branch.
     *
     * <p>OpenCode 1.18.13 intentionally skips its internal Git credential and author setup when
     * {@code USE_GITHUB_TOKEN=true}. Because checkout credentials remain disabled, the workflow
     * must install a local, short-lived authorization header and local author identity before
     * starting OpenCode, then remove the authorization header even when the process fails or
     * times out.</p>
     */
    @Test
    void bootstrapsAndRemovesDirectTokenGitCredentials() {
        assertTrue(workflow.contains(
                "git_config_key=\"http.https://github.com/.extraheader\""
        ));
        assertTrue(workflow.contains("cleanup_git_credentials()"));
        assertTrue(workflow.contains("trap cleanup_git_credentials EXIT"));
        assertTrue(workflow.contains(
                "printf 'x-access-token:%s' \"${GITHUB_TOKEN}\""
        ));
        assertTrue(workflow.contains(
                "git config --local \"${git_config_key}\" "
                        + "\"AUTHORIZATION: basic ${github_basic_auth}\""
        ));
        assertTrue(workflow.contains(
                "git config --local user.name \"opencode-agent[bot]\""
        ));
        assertTrue(workflow.contains(
                "git config --local user.email "
                        + "\"opencode-agent[bot]@users.noreply.github.com\""
        ));
        assertTrue(workflow.contains("unset github_basic_auth"));
        assertFalse(workflow.contains("AGENT: build"));
    }

    /**
     * Verifies that the repository's NVIDIA NIM secret is the only model credential and is
     * mapped to the environment variable documented by OpenCode's NVIDIA provider.
     */
    @Test
    void usesOnlyTheNvidiaNimCredentialAndExplicitModel() {
        assertEquals(Set.of("NVIDIA_NIM_API_KEY"), referencedSecrets());
        assertTrue(workflow.contains(
                "NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}"
        ));
        assertTrue(workflow.contains(
                "MODEL: nvidia/qwen/qwen3-coder-480b-a35b-instruct"
        ));
        assertTrue(workflow.contains("SHARE: \"false\""));
        assertTrue(workflow.contains("USE_GITHUB_TOKEN: \"true\""));

        String lowerCaseWorkflow = workflow.toLowerCase(Locale.ROOT);
        assertFalse(lowerCaseWorkflow.contains("copilot"));
        assertFalse(workflow.contains("ANTHROPIC_API_KEY"));
        assertFalse(workflow.contains("OPENAI_API_KEY"));
    }

    /**
     * Verifies least-privilege repository access and rejects an unnecessary OIDC token path.
     */
    @Test
    void grantsOnlyRepositoryMaintenancePermissions() {
        assertTrue(workflow.contains("contents: write"));
        assertTrue(workflow.contains("pull-requests: write"));
        assertTrue(workflow.contains("issues: write"));
        assertTrue(workflow.contains("checks: read"));
        assertTrue(workflow.contains("statuses: read"));
        assertFalse(workflow.contains("id-token:"));
        assertFalse(workflow.contains("actions: write"));
        assertFalse(workflow.contains("security-events: write"));
    }

    /**
     * Verifies that the model prompt preserves independent review and deterministic merge
     * authority instead of granting the development agent governance powers.
     */
    @Test
    void promptForbidsReviewMergeAndProtectedBranchBypass() {
        assertTrue(workflow.contains("Start every run by inspecting every open pull request"));
        assertTrue(workflow.contains("exact current head"));
        assertTrue(workflow.contains("Never approve or merge a pull request"));
        assertTrue(workflow.contains("Never push directly to develop or main"));
        assertTrue(workflow.contains("Do not bypass branch protection"));
        assertTrue(workflow.contains("Do not alter the existing review agent"));
        assertTrue(workflow.contains("Do not change any review-agent secret name"));
        assertTrue(workflow.contains("Do not modify .github/workflows/"));
        assertTrue(workflow.contains("automation-maintenance"));
        assertTrue(workflow.contains("Do not create a second development pull request"));
        assertTrue(workflow.contains("Do not print, echo, summarize, or expose secret values"));
    }

    /**
     * Extracts every repository-secret name referenced by the workflow.
     *
     * @return immutable set of referenced GitHub Actions secret identifiers
     */
    private static Set<String> referencedSecrets() {
        Matcher matcher = SECRET_REFERENCE.matcher(workflow);
        Set<String> secretNames = new java.util.HashSet<>();
        while (matcher.find()) {
            secretNames.add(matcher.group(1));
        }
        return Set.copyOf(secretNames);
    }

    /**
     * Finds the reactor root from either root or module-local Maven execution.
     *
     * @return absolute path that contains the root Maven project
     * @throws IllegalStateException when no repository or Maven root can be found
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
