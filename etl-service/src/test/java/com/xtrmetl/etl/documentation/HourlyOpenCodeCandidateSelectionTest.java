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
 * Guards the jq variable scope used to remove an updated PR branch from new-branch candidates.
 *
 * <p>Inside {@code $existing_refs | index(...)}, the jq input changes from the branch object to the
 * reference array. Reading {@code .name} after that pipe therefore fails at runtime. Production
 * must capture the branch name first and pass the scalar variable to {@code index}.</p>
 */
class HourlyOpenCodeCandidateSelectionTest {

    /**
     * Requires branch-name capture before indexing the existing-PR reference array.
     *
     * @throws IOException when the workflow cannot be read
     */
    @Test
    void capturesBranchNameBeforeExistingReferenceLookup() throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(workflow.contains(".name as $branch_name"));
        assertTrue(workflow.contains("index($branch_name)"));
        assertFalse(workflow.contains("index(.name)"));
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
