package com.xtrmetl.etl.documentation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the pull-request concurrency contract for workflows that consume hosted runners.
 */
class WorkflowConcurrencyTest {

    @ParameterizedTest(name = "{0} cancels superseded pull-request runs")
    @ValueSource(strings = {
            ".github/workflows/ci.yml",
            ".github/workflows/sbom.yml",
            ".github/workflows/dependency-review.yml"
    })
    void pullRequestWorkflowCancelsSupersededRuns(String workflowPath) throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(workflowPath),
                StandardCharsets.UTF_8
        );

        assertTrue(workflow.contains("concurrency:"),
                workflowPath + " must define a concurrency group");
        assertTrue(workflow.contains("github.workflow"),
                workflowPath + " must isolate concurrency by workflow");
        assertTrue(workflow.contains("github.event.pull_request.number || github.ref"),
                workflowPath + " must group PR runs by pull request and non-PR runs by ref");
        assertTrue(workflow.contains(
                        "cancel-in-progress: ${{ github.event_name == 'pull_request' }}"
                ), workflowPath + " must cancel only superseded pull-request runs");
    }

    /**
     * Finds the repository root from either a reactor or module-local Maven invocation.
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
