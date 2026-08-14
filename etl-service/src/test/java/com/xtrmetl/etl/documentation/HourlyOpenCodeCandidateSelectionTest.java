package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the candidate-selection and publication boundaries used by the hourly OpenCode workflow.
 *
 * <p>Inside {@code $existing_refs | index(...)}, the jq input changes from the branch object to the
 * reference array. Reading {@code .name} after that pipe therefore fails at runtime, so production
 * must capture the branch name first and pass the scalar variable to {@code index}. Updated
 * existing pull requests must also preserve their pre-agent head through local candidate capture,
 * isolated branch publication, and pull-request publication so policy-file changes are rejected
 * before and after the remote branch update.</p>
 */
class HourlyOpenCodeCandidateSelectionTest {

    /**
     * Requires branch-name capture before indexing the existing-PR reference array.
     *
     * @throws IOException when the workflow cannot be read
     */
    @Test
    void capturesBranchNameBeforeExistingReferenceLookup() throws IOException {
        String workflow = workflowText();

        assertTrue(workflow.contains(".name as $branch_name"));
        assertTrue(workflow.contains("index($branch_name)"));
        assertFalse(workflow.contains("index(.name)"));
    }

    /**
     * Requires unexpected remote publication movement to use a diagnostic that is accurate for
     * either one or many remote candidates.
     *
     * @throws IOException when the workflow cannot be read
     */
    @Test
    void reportsAnyUnexpectedRemotePublicationCandidateAccurately() throws IOException {
        String workflow = workflowText();

        assertTrue(workflow.contains(
                "Remote publication candidates changed during the model job; "
                        + "refusing to race another writer"
        ));
        assertFalse(workflow.contains(
                "Multiple agent publication candidates were detected remotely"
        ));
    }

    /**
     * Requires policy-file rejection at both deterministic publication boundaries.
     *
     * <p>The model job now has read-only repository credentials and produces only a local commit.
     * The isolated branch publisher must re-bind an existing pull request to its exact pre-agent
     * head, validate the complete candidate path set before its sole branch push, and refuse policy
     * files. The separate pull-request publisher must then compare that pre-agent head with the
     * exact published head and independently reject the same policy paths. This keeps the original
     * guard effective after publication authority was removed from the model-executing job.</p>
     *
     * @throws IOException when the workflow cannot be read
     */
    @Test
    void rejectsPolicyChangesOnUpdatedExistingPullRequests() throws IOException {
        String workflow = workflowText();
        String branchPublisher = between(
                workflow,
                "\n  publish-agent-branch:\n",
                "\n  publish-agent-pull-request:\n"
        );
        String branchExistingPrPreflight = between(
                branchPublisher,
                "if [[ \"${kind}\" == \"existing_pr\" ]]; then",
                "elif [[ \"${kind}\" == \"new_branch\" ]]; then"
        );
        String pullRequestPublisher = between(
                workflow,
                "\n  publish-agent-pull-request:\n",
                "\n  authorize-exact-head-checks:\n"
        );
        String pullRequestExistingPrValidation = between(
                pullRequestPublisher,
                "if [[ \"${kind}\" == \"existing_pr\" ]]; then",
                "elif [[ \"${kind}\" == \"new_branch\" ]]; then"
        );

        assertTrue(workflow.contains("before_head_sha: $before_head_sha"));
        assertTrue(branchExistingPrPreflight.contains(
                "before_head=\"$(jq -r '.before_head_sha' <<<\"${metadata}\")\""
        ));
        assertTrue(branchExistingPrPreflight.contains("and .head.sha == $before_head"));
        assertTrue(branchPublisher.contains(
                "git log --format= --name-only \"${predecessor_sha}..${candidate_head}\""
        ));
        assertTrue(branchPublisher.contains(
                "grep -Eq '(^\\.github/|(^|/)CODEOWNERS$)' \"${changed_paths_file}\""
        ));
        assertAppearsBefore(
                branchPublisher,
                "grep -Eq '(^\\.github/|(^|/)CODEOWNERS$)' \"${changed_paths_file}\"",
                "git push origin \"${candidate_head}:refs/heads/${head_ref}\""
        );

        assertTrue(pullRequestExistingPrValidation.contains(
                "before_head=\"$(jq -r '.before_head_sha' <<<\"${AGENT_CANDIDATE}\")\""
        ));
        assertTrue(pullRequestExistingPrValidation.contains(
                "comparison=\"$(gh api \"/repos/${repository}/compare/${before_head}...${expected_head}\")\""
        ));
        assertTrue(pullRequestExistingPrValidation.contains(".files[].filename"));
        assertTrue(pullRequestExistingPrValidation.contains("startswith(\".github/\")"));
        assertTrue(pullRequestExistingPrValidation.contains("or . == \"CODEOWNERS\""));
        assertTrue(pullRequestExistingPrValidation.contains("or endswith(\"/CODEOWNERS\")"));
    }

    /**
     * Requires one security-sensitive marker to occur before another in the same workflow section.
     *
     * @param text workflow section being checked
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
     * Reads the normalized hourly workflow text.
     *
     * @return workflow content with Unix line endings
     * @throws IOException when the workflow cannot be read
     */
    private static String workflowText() throws IOException {
        return Files.readString(
                projectRoot().resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }

    /**
     * Extracts a required text section between two unique markers.
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
     * Finds the Maven reactor root from root-level or module-local execution.
     *
     * @return absolute project root
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
