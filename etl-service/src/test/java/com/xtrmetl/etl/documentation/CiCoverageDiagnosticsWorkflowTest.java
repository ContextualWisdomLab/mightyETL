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
 * Guards the failure diagnostics for every production class governed by the strict JaCoCo policy.
 *
 * <p>The coverage gate protects more than the durable-job service. When a class fails the 100%
 * statement or branch threshold, the CI log must identify missed methods and source lines for
 * every configured target rather than reporting only one historical class. This test keeps the
 * diagnostic vocabulary synchronized with the authoritative Maven coverage configuration.</p>
 */
class CiCoverageDiagnosticsWorkflowTest {

    private static String workflow;

    /**
     * Reads the CI workflow with normalized line endings for deterministic platform behavior.
     *
     * @throws IOException when the workflow cannot be read as UTF-8 text
     */
    @BeforeAll
    static void readWorkflow() throws IOException {
        Path workflowPath = projectRoot().resolve(".github/workflows/ci.yml");
        assertTrue(Files.exists(workflowPath), "The CI workflow must exist");
        workflow = Files.readString(workflowPath, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    /**
     * Requires one iterable diagnostic map for every zero-missed production coverage target.
     */
    @Test
    void diagnosesEveryStrictCoverageTarget() {
        assertTrue(workflow.contains(
                "\"com/xtrmetl/etl/job/EtlJobService\": \"EtlJobService.java\""
        ));
        assertTrue(workflow.contains(
                "\"com/xtrmetl/etl/job/EtlJobReplayService\": "
                        + "\"EtlJobReplayService.java\""
        ));
        assertTrue(workflow.contains(
                "\"com/xtrmetl/etl/controller/EtlJobController\": "
                        + "\"EtlJobController.java\""
        ));
        assertTrue(workflow.contains(
                "\"com/xtrmetl/etl/service/Sha256Digest\": \"Sha256Digest.java\""
        ));
        assertTrue(workflow.contains(
                "for class_name, source_name in coverage_targets.items():"
        ));
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
