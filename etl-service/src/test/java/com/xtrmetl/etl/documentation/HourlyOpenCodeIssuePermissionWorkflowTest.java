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
 * Guards least-privilege issue access for the scheduled OpenCode maintenance workflow.
 *
 * <p>The maintenance agent may inspect issues while selecting one bounded product gap, but no
 * current workflow operation mutates issue state. The repository token therefore requires only
 * read access to issues; retaining {@code issues: write} would add unnecessary authority to the
 * model-executing job.</p>
 */
class HourlyOpenCodeIssuePermissionWorkflowTest {

    /** Verifies that the model-executing job can read issues but cannot mutate them. */
    @Test
    void grantsReadOnlyIssuePermissionToMaintenanceAgent() throws IOException {
        String workflow = Files.readString(
                projectRoot().resolve(".github/workflows/hourly-opencode-maintenance.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        int maintenanceStart = workflow.indexOf("  maintain-repository:");
        int publisherStart = workflow.indexOf("  publish-agent-pull-request:");
        assertTrue(maintenanceStart >= 0, "The maintenance job must exist");
        assertTrue(publisherStart > maintenanceStart, "The publisher must follow maintenance");

        String maintenanceJob = workflow.substring(maintenanceStart, publisherStart);
        assertTrue(maintenanceJob.contains("issues: read"));
        assertFalse(maintenanceJob.contains("issues: write"));
    }

    /**
     * Finds the repository root from either reactor-root or module-local execution.
     *
     * @return absolute repository root
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
