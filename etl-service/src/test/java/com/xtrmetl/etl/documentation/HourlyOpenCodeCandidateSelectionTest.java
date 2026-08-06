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
 * reference array. Reading {@code .name} after that pipe therefore fails at runtime. Production
 * must capture the branch name first and pass the scalar variable to {@code index}. Updated
 * existing pull requests must also be compared with their pre-agent heads so policy-file changes
 * cannot bypass the new-branch publication guard.</p>
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
     * Requires updated existing pull requests to reject agent-introduced policy-file changes.
     *
     * <p>The model job may update an already-open pull request. The deterministic publisher must
     * therefore retain that pull request's pre-agent head, compare it with the candidate head, and
     * apply the same {@code .github/**} and {@code CODEOWNERS} exclusion used for new branches.</p>
     *
     * @throws IOException when the workflow cannot be read
     */
    @Test
    void rejectsPolicyChangesOnUpdatedExistingPullRequests() throws IOException {
        String workflow = workflowText();
        String existingPrBlock = between(
                workflow,
                "if [[ \"${kind}\" == \"existing_pr\" ]]; then",
                "elif [[ \"${kind}\" == \"new_branch\" ]]; then"
        );

        assertTrue(workflow.contains("before_head_sha: $before[$number_key]"));
        assertTrue(existingPrBlock.contains(
                "before_head=\"$(jq -r '.before_head_sha' <<<\"${AGENT_CANDIDATE}\")\""
        ));
        assertTrue(existingPrBlock.contains(
                "comparison=\"$(gh api \"/repos/${repository}/compare/${before_head}...${expected_head}\")\""
        ));
        assertTrue(existingPrBlock.contains("startswith(\".github/\")"));
        assertTrue(existingPrBlock.contains("or . == \"CODEOWNERS\""));
        assertTrue(existingPrBlock.contains("or endswith(\"/CODEOWNERS\")"));
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
        int end = text.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, () -> "Missing start marker: " + startMarker);
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
