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
 * Guards the fail-closed contract of the scheduled pull-request disposition workflow.
 */
class HourlyPrDispositionWorkflowTest {

    private static String workflow;

    @BeforeAll
    static void readWorkflow() throws IOException {
        workflow = Files.readString(
                projectRoot().resolve(".github/workflows/hourly-pr-disposition.yml"),
                StandardCharsets.UTF_8
        );
    }

    @Test
    void neverChecksOutOrExecutesPullRequestCode() {
        assertFalse(workflow.contains("actions/checkout@"));
        assertFalse(workflow.contains("pull_request_target"));
        assertTrue(workflow.contains("head_repo"));
        assertTrue(workflow.contains("TRUSTED_AUTHOR"));
    }

    @Test
    void requiresLatestDecisiveReviewState() {
        assertTrue(workflow.contains(
                "map(select(.state == \"APPROVED\" or .state == \"CHANGES_REQUESTED\"))"
        ));
        assertTrue(workflow.contains("map(max_by(.submitted_at // \"\"))"));
        assertTrue(workflow.contains("latest decisive review state includes requested changes"));
    }

    @Test
    void requiresResolvedCurrentReviewThreads() {
        assertTrue(workflow.contains("reviewThreads(first: 100, after: $endCursor)"));
        assertTrue(workflow.contains(".isResolved == false and .isOutdated == false"));
        assertTrue(workflow.contains("unresolved review threads"));
    }

    @Test
    void requiresSuccessfulNamedChecksRatherThanSkippedChecks() {
        assertTrue(workflow.contains("successful_names"));
        assertTrue(workflow.contains(".conclusion == \"success\""));
        assertTrue(workflow.contains("not every required check completed successfully"));
    }

    @Test
    void protectsWorkflowChangesAndHeadMovement() {
        assertTrue(workflow.contains("automerge-workflow"));
        assertTrue(workflow.contains("-f sha=\"${head_sha}\""));
        assertTrue(workflow.contains("merge_method=\"squash\""));
    }

    @Test
    void mergeRejectionSkipsOnlyTheAffectedPullRequest() {
        assertTrue(workflow.contains("if ! result=$(gh api -X PUT"));
        assertTrue(workflow.contains("GitHub merge request failed"));
        assertTrue(workflow.contains("continue"));
    }

    /**
     * Finds the reactor root from either root or module-local Maven execution.
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
