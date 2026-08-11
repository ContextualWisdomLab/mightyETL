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
 * Guards pull-request quality evidence against GitHub's generated merge revision.
 *
 * <p>A pull-request workflow normally checks out {@code github.sha}, which is the synthetic merge
 * commit rather than the contributor branch's exact current head. Branch protection may consume
 * those results, but mightyETL's expected-head policy also requires direct evidence for the literal
 * head SHA. Each source-executing workflow therefore binds checkout and an explicit identity
 * assertion to the pull-request head, while push runs fall back to their event SHA.</p>
 */
class ExactHeadWorkflowCheckoutTest {

    private static final String EXACT_SOURCE_EXPRESSION =
            "${{ github.event.pull_request.head.sha || github.sha }}";

    @Test
    void continuousIntegrationChecksOutAndAssertsTheExactSourceRevision() throws IOException {
        assertExactHeadCheckout(".github/workflows/ci.yml");
    }

    @Test
    void sbomGenerationChecksOutAndAssertsTheExactSourceRevision() throws IOException {
        assertExactHeadCheckout(".github/workflows/sbom.yml");
    }

    private static void assertExactHeadCheckout(String relativePath) throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(relativePath),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(
                workflow.contains("ref: " + EXACT_SOURCE_EXPRESSION),
                relativePath + " must check out the literal pull-request head"
        );
        assertTrue(
                workflow.contains("persist-credentials: false"),
                relativePath + " must not persist the checkout credential"
        );
        assertTrue(
                workflow.contains(
                        "test \"$(git rev-parse HEAD)\" = \"" + EXACT_SOURCE_EXPRESSION + "\""
                ),
                relativePath + " must fail when the checked-out revision is not the expected head"
        );
        assertFalse(
                workflow.contains("ref: ${{ github.sha }}"),
                relativePath + " must not bind pull-request source execution to the merge revision"
        );
    }

    /**
     * Finds the repository root from repository-root or module-local Maven execution.
     *
     * @return repository root containing the workflow files
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
