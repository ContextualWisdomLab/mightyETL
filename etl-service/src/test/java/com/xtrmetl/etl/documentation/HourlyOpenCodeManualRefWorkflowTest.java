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
 * Prevents the scheduled maintenance workflow from executing repository-controlled code from a
 * manually selected feature branch or tag.
 *
 * <p>GitHub manual workflow dispatch can select a non-default ref. The maintenance workflow has
 * repository write authority and receives the NVIDIA model credential, so its trusted workflow
 * source must be schedule-only and its checkout must explicitly bind to the repository default
 * branch. This test makes both authority boundaries visible to beginning maintainers.</p>
 */
class HourlyOpenCodeManualRefWorkflowTest {

    private static String workflow;

    /**
     * Reads the workflow with normalized line endings for deterministic cross-platform checks.
     *
     * @throws IOException when the workflow cannot be read as UTF-8 text
     */
    @BeforeAll
    static void readWorkflow() throws IOException {
        Path workflowPath = projectRoot().resolve(
                ".github/workflows/hourly-opencode-maintenance.yml"
        );
        assertTrue(Files.exists(workflowPath), "The maintenance workflow must exist");
        workflow = Files.readString(workflowPath, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    /**
     * Requires schedule-only invocation and an explicit protected default-branch checkout.
     */
    @Test
    void rejectsManualFeatureRefsAndPinsTheDefaultBranchCheckout() {
        assertFalse(
                workflow.contains("workflow_dispatch:"),
                "Manual dispatch must not allow a feature branch or tag to supply workflow code"
        );
        assertTrue(
                workflow.contains("ref: ${{ github.event.repository.default_branch }}"),
                "Checkout must explicitly use the protected repository default branch"
        );
    }

    /**
     * Finds the repository root from either root or module-local Maven execution.
     *
     * @return absolute repository root containing the root Maven project
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
