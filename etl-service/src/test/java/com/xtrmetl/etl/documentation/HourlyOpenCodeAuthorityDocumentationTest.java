package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the authoritative OpenCode security doctoring aligned with the executable workflow.
 *
 * <p>This contract prevents acquisition and security evidence from describing superseded token
 * authority. The model-executing job must remain repository-read-only, while branch publication,
 * pull-request publication, and workflow-run authorization stay in separate deterministic jobs.</p>
 */
class HourlyOpenCodeAuthorityDocumentationTest {

    private static String workflow;
    private static String doctoring;
    private static String design;
    private static String plan;
    private static String changelog;

    /**
     * Reads the executable workflow as raw normalized YAML and the prose sources using canonical
     * whitespace so job-scoped permission checks retain YAML boundaries while prose wrapping does
     * not change the documentation contract.
     *
     * @throws IOException when a checked repository document cannot be read
     */
    @BeforeAll
    static void readAuthoritySources() throws IOException {
        Path root = projectRoot();
        workflow = Files.readString(
                root.resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
        doctoring = canonicalWhitespace(Files.readString(
                root.resolve("docs/doctoring/github-token-exact-head-check-authorization-evidence.md"),
                StandardCharsets.UTF_8
        ));
        design = canonicalWhitespace(Files.readString(
                root.resolve("docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md"),
                StandardCharsets.UTF_8
        ));
        plan = canonicalWhitespace(Files.readString(
                root.resolve("docs/superpowers/plans/2026-08-04-hourly-opencode-maintenance-plan.md"),
                StandardCharsets.UTF_8
        ));
        changelog = canonicalWhitespace(Files.readString(
                root.resolve("CHANGELOG.md"),
                StandardCharsets.UTF_8
        ));
    }

    /** Requires doctoring to state that the model job has no repository write permission. */
    @Test
    void documentsModelJobAsReadOnlyGitHubAuthority() {
        String maintenanceJob = between(
                workflow,
                "  maintain-repository:",
                "  publish-agent-branch:"
        );
        assertTrue(maintenanceJob.contains("actions: read"));
        assertTrue(maintenanceJob.contains("contents: read"));
        assertTrue(maintenanceJob.contains("issues: read"));
        assertTrue(maintenanceJob.contains("pull-requests: read"));
        assertFalse(maintenanceJob.contains("contents: write"));
        assertFalse(maintenanceJob.contains("issues: write"));

        assertTrue(doctoring.contains(
                "`maintain-repository` is the only job that checks out source or runs OpenCode. "
                        + "It has read-only GitHub authority"
        ));
        assertTrue(doctoring.contains(
                "actions: read checks: read contents: read issues: read pull-requests: read "
                        + "security-events: read statuses: read"
        ));
    }

    /** Requires doctoring to identify the isolated deterministic jobs that own each write. */
    @Test
    void documentsSeparatedBranchPullRequestAndActionsWriters() {
        assertTrue(workflow.contains("publish-agent-branch:"));
        assertTrue(workflow.contains("publish-agent-pull-request:"));
        assertTrue(workflow.contains("authorize-exact-head-checks:"));

        assertTrue(doctoring.contains(
                "`publish-agent-branch` is the sole `contents: write` holder"
        ));
        assertTrue(doctoring.contains(
                "`publish-agent-pull-request` is the sole `pull-requests: write` holder"
        ));
        assertTrue(doctoring.contains(
                "`authorize-exact-head-checks` is the sole `actions: write` holder"
        ));
        assertTrue(doctoring.contains(
                "The model-executing job receives none of those write permissions"
        ));
    }

    /** Requires the design and implementation plan to describe the live four-job topology. */
    @Test
    void documentsTheCurrentFourJobTopologyAndCredentialLifecycle() {
        assertTrue(design.contains("Use four physically separated GitHub Actions jobs"));
        assertTrue(design.contains("`maintain-repository` creates local commits only"));
        assertTrue(design.contains("The model job uses an ephemeral `GIT_ASKPASS` script"));
        assertTrue(design.contains(
                "`publish-agent-branch` uses the repository-local `!gh auth git-credential` helper"
        ));

        assertTrue(plan.contains(
                "Four jobs separate model execution, deterministic branch publication, "
                        + "deterministic draft-PR publication, and exact-head workflow-run authorization"
        ));
        assertTrue(plan.contains("Set model-job permissions to read-only GitHub authority"));
        assertTrue(plan.contains("Create local commits only; do not push from the model job"));
        assertTrue(plan.contains("## Task 4 — Publish the exact branch in an isolated job"));
    }

    /** Requires durable release notes to avoid time-sensitive endpoint-pricing claims. */
    @Test
    void avoidsTimeSensitiveFreeEndpointClaimsInTheChangelog() {
        assertFalse(changelog.contains("current free NVIDIA"));
        assertTrue(changelog.contains("NVIDIA `deepseek-ai/deepseek-v4-pro` endpoint"));
    }

    /**
     * Extracts a required raw-text section between ordered markers.
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
     * Collapses semantically irrelevant whitespace for stable Markdown prose assertions.
     *
     * @param value repository text to normalize
     * @return one-space canonical representation
     */
    private static String canonicalWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * Finds the repository root from either reactor-root or module-local Maven execution.
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
