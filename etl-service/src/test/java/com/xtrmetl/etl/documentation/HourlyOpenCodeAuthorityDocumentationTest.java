package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    /**
     * Reads the executable workflow and its authoritative security evidence using canonical
     * whitespace so prose wrapping cannot change the contract.
     *
     * @throws IOException when either checked repository document cannot be read
     */
    @BeforeAll
    static void readAuthoritySources() throws IOException {
        Path root = projectRoot();
        workflow = canonicalWhitespace(Files.readString(
                root.resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ));
        doctoring = canonicalWhitespace(Files.readString(
                root.resolve("docs/doctoring/github-token-exact-head-check-authorization-evidence.md"),
                StandardCharsets.UTF_8
        ));
    }

    /** Requires doctoring to state that the model job has no repository write permission. */
    @Test
    void documentsModelJobAsReadOnlyGitHubAuthority() {
        assertTrue(workflow.contains("maintain-repository:"));
        assertTrue(workflow.contains("actions: read"));
        assertTrue(workflow.contains("contents: read"));
        assertTrue(workflow.contains("issues: read"));
        assertTrue(workflow.contains("pull-requests: read"));

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

    /**
     * Collapses semantically irrelevant whitespace for stable Markdown and YAML prose assertions.
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
