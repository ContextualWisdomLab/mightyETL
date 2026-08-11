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
     * endings and whitespace so the contract behaves identically on every supported operating
     * system.
     *
     * @throws IOException when either authoritative UTF-8 source cannot be read
     */
    @BeforeAll
    static void readSchedulerPolicy() throws IOException {
        Path root = projectRoot();
        agentPolicy = canonicalWhitespace(
                Files.readString(root.resolve("AGENTS.md"), StandardCharsets.UTF_8)
        );
        workflow = canonicalWhitespace(
                Files.readString(
                        root.resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                        StandardCharsets.UTF_8
                )
        );
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

    /** Requires root-cause evidence and a realistic execution decision before remediation. */
    @Test
    void performsRootCauseAnalysisAndFeasibilityClassificationBeforeActing() {
        assertTrue(workflow.contains(
                "For every failing or blocked outcome, perform root-cause analysis before choosing "
                        + "a remediation."
        ));
        assertTrue(workflow.contains(
                "source, configuration, permission, quota, runner, provider, dependency, or policy "
                        + "boundary"
        ));
        assertTrue(workflow.contains(
                "Generate bounded remediation options that address the identified cause."
        ));
        assertTrue(workflow.contains(
                "test each option's feasibility against current permissions, branch protection, "
                        + "tool capability, runtime and compute budgets, dependency state, and path "
                        + "ownership"
        ));
        assertTrue(workflow.contains(
                "Classify each option as executable now, requires an external actor, or unsafe or "
                        + "infeasible."
        ));
    }

    /** Requires the scheduler to act, verify, and continue after an infeasible preferred option. */
    @Test
    void executesTheBestFeasibleActionAndContinuesAfterExternalOnlyBlockers() {
        assertTrue(workflow.contains(
                "Execute the highest-impact safe option that is executable in this run"
        ));
        assertTrue(workflow.contains("rerun the exact failing test or gate"));
        assertTrue(workflow.contains(
                "If the preferred option requires an external actor or is infeasible, keep that gate "
                        + "fail-closed and immediately choose the next safe feasible non-overlapping "
                        + "remediation or independent bounded product slice instead of stopping."
        ));
        assertTrue(workflow.contains(
                "A pull request with only external blockers is not source-actionable."
        ));
        assertTrue(workflow.contains(
                "When no open pull request is source-actionable, whether or not blocked pull requests "
                        + "remain open"
        ));
    }

    /** Requires every completed or deferred action to return to a live work-conserving queue. */
    @Test
    void treatsEveryActionAsIntermediateAndReturnsToTheExecutableQueue() {
        assertTrue(workflow.contains(
                "Completing an action is intermediate state, not an invocation endpoint."
        ));
        assertTrue(workflow.contains(
                "After every remediation, commit, documentation update, test result, deferred "
                        + "blocker, or completed slice, return to the highest-value safe executable "
                        + "queue."
        ));
        assertTrue(workflow.contains(
                "The one remote publication candidate limit constrains mutation output, not further "
                        + "read-only diagnosis, testing, or documentation analysis after a candidate "
                        + "is prepared."
        ));
        assertTrue(workflow.contains(
                "Queued checks, reviews, and provider waits are local deferred items, not reasons to "
                        + "idle."
        ));
        assertTrue(workflow.contains(
                "Same-branch writer movement freezes only that branch; continue safe work on other "
                        + "non-overlapping branches or read-only lanes."
        ));
    }

    /** Requires two clean fresh exit sweeps before finite-run termination. */
    @Test
    void requiresDoubleFreshExitSweepBeforeTermination() {
        assertTrue(workflow.contains(
                "Before terminating, perform a fresh whole-repository sweep of pull requests, "
                        + "issues, checks, reviews, security, stack ancestry, documentation, release "
                        + "readiness, and product gaps."
        ));
        assertTrue(workflow.contains(
                "If that sweep finds any safe executable item, execute the highest-value item and "
                        + "restart the exit sweep count."
        ));
        assertTrue(workflow.contains(
                "Terminate only on genuine finite run-budget exhaustion or after a second "
                        + "consecutive fresh sweep proves no safe executable action remains."
        ));
        assertTrue(workflow.contains("Routine status narration is not work."));
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
     * Collapses semantically irrelevant Markdown and platform whitespace before phrase matching.
     *
     * @param value UTF-8 text whose prose contract must be compared independently of wrapping
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