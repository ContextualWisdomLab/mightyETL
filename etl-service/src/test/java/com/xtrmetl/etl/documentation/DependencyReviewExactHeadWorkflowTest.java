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
 * Keeps dependency review bound to GitHub pull-request event semantics and an immutable action revision.
 *
 * <p>The dependency-review action derives the pull request base and head from the event payload for
 * {@code pull_request} and {@code pull_request_target}. Its {@code base-ref}/{@code head-ref} inputs
 * are documented for other event types only, so supplying them here would be ignored and would create
 * misleading exact-head evidence.</p>
 */
class DependencyReviewExactHeadWorkflowTest {

    @Test
    void dependencyReviewUsesPullRequestEventRefsWithPinnedAction() throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(".github/workflows/dependency-review.yml"),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", " ");

        assertTrue(workflow.contains("pull_request:"));
        assertFalse(workflow.contains("base-ref:"));
        assertFalse(workflow.contains("head-ref:"));
        assertTrue(workflow.contains(
                "uses: actions/dependency-review-action@"
                        + "a1d282b36b6f3519aa1f3fc636f609c47dddb294"
        ));
        assertTrue(workflow.contains("fail-on-severity: high"));
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
