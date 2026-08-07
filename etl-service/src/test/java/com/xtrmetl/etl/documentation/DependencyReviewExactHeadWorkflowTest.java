package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps dependency-delta evidence explicitly bound to the pull request's exact base and head SHAs.
 */
class DependencyReviewExactHeadWorkflowTest {

    @Test
    void dependencyReviewPassesExactEventBaseAndHeadToPinnedAction() throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(".github/workflows/dependency-review.yml"),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", " ");

        assertTrue(workflow.contains(
                "base-ref: ${{ github.event.pull_request.base.sha }}"
        ));
        assertTrue(workflow.contains(
                "head-ref: ${{ github.event.pull_request.head.sha }}"
        ));
        assertTrue(workflow.contains(
                "uses: actions/dependency-review-action@"
                        + "a1d282b36b6f3519aa1f3fc636f609c47dddb294"
        ));
    }

    /** @return repository root from reactor-root or module-local execution */
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
