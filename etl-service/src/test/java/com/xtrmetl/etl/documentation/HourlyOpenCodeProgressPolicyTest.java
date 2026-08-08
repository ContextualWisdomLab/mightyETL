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
 * Specifies how scheduled OpenCode maintenance must keep making safe repository-local progress
 * while external review, check, or read-only dependency gates remain unavailable.
 *
 * <p>The policy deliberately keeps merge evidence fail-closed while preventing an unrelated
 * external wait from stopping bounded, non-conflicting mightyETL work. It also preserves stack
 * integrity by prohibiting work on invalid downstream boundaries.</p>
 */
class HourlyOpenCodeProgressPolicyTest {

    private static String agentPolicy;
    private static String workflow;

    /**
     * Reads the scheduler's repository instruction and workflow inputs once with normalized line
     * endings so the contract behaves identically on every supported operating system.
     *
     * @throws IOException when either authoritative UTF-8 source cannot be read
     */
    @BeforeAll
    static void readSchedulerPolicy() throws IOException {
        Path root = projectRoot();
        agentPolicy = Files.readString(root.resolve("AGENTS.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        workflow = Files.readString(
                        root.resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                        StandardCharsets.UTF_8
                )
                .replace("\r\n", "\n");
    }

    /**
     * Requires external-only latency to remain a merge blocker without becoming a blanket
     * development stop condition.
     */
    @Test
    void continuesOneBoundedNonConflictingSliceWhenNoPullRequestIsSourceActionable() {
        assertTrue(agentPolicy.contains("## Scheduled maintenance progress contract"));
        assertTrue(agentPolicy.contains(
                "External review, approval, check, or read-only dependency latency is not a "
                        + "reason to stop all productive mightyETL work."
        ));
        assertTrue(agentPolicy.contains(
                "A pull request is source-actionable only when its exact current head has a valid "
                        + "repository-local finding or failing source gate that mightyETL can repair."
        ));
        assertTrue(agentPolicy.contains(
                "When no open pull request is source-actionable, select exactly one non-conflicting "
                        + "bounded mightyETL slice from the protected `develop` head."
        ));
        assertTrue(agentPolicy.contains(
                "Do not deepen an invalid stack or modify a blocked stack branch merely to appear "
                        + "productive."
        ));
        assertTrue(agentPolicy.contains(
                "The independent slice must not depend on, retarget, rewrite, or overlap files "
                        + "changed by the invalid stack."
        ));
    }

    /** Keeps every separately leased repository outside this scheduler's write authority. */
    @Test
    void preservesReadOnlyDependencyLeasesWhileContinuingLocalWork() {
        assertTrue(agentPolicy.contains(
                "ContextualWisdomLab/.github, naruon, contextual-orchestrator, and every separately "
                        + "leased repository remain read-only."
        ));
        assertTrue(agentPolicy.contains(
                "Inspect their exact integration state, but never mutate, dispatch a write-capable "
                        + "agent, or post a mutation-trigger comment there."
        ));
        assertTrue(workflow.contains("Checkout protected default-branch source"));
        assertTrue(workflow.contains("opencode run --model \"${MODEL}\" --auto"));
        assertTrue(workflow.contains("Use the repository-scoped token for read operations only"));
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
