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
 * Guards the repository-local, hourly caller for the centrally governed pull-request maintenance
 * workflow.
 *
 * <p>The caller schedules review inspection, bounded repair dispatch, current-head check
 * revalidation, and policy-governed merge handling without copying the central implementation or
 * inheriting unrelated repository secrets.</p>
 */
class HourlyPrMaintenanceCallerTest {

    private static String workflow;
    private static String legacyDispositionWorkflow;
    private static String documentation;
    private static String changelog;

    /**
     * Reads the production caller and its operator evidence once with normalized line endings.
     *
     * @throws IOException when a required repository artifact exists but cannot be read
     */
    @BeforeAll
    static void readWorkflow() throws IOException {
        Path root = projectRoot();
        Path workflowPath = root.resolve(
                ".github/workflows/hourly-pr-maintenance.yml"
        );
        assertTrue(Files.exists(workflowPath), "The hourly PR maintenance caller must exist");
        workflow = readNormalized(workflowPath);
        legacyDispositionWorkflow = readNormalized(
                root.resolve(".github/workflows/hourly-pr-disposition.yml")
        );
        documentation = readNormalized(root.resolve("docs/hourly-pr-disposition.md"));
        changelog = readNormalized(root.resolve("CHANGELOG.md"));
    }

    /** Verifies one serialized run each hour with an explicit manual recovery trigger. */
    @Test
    void schedulesOneHourlySerializedMaintenanceRun() {
        assertTrue(workflow.contains("cron: \"17 * * * *\""));
        assertTrue(workflow.contains("workflow_dispatch:"));
        assertTrue(workflow.contains("group: hourly-pr-maintenance"));
        assertTrue(workflow.contains("cancel-in-progress: false"));
    }

    /** Verifies immutable central implementation reuse rather than repository-local duplication. */
    @Test
    void pinsTheCentralSchedulerToAnImmutableCommit() {
        assertTrue(workflow.contains(
                "uses: ContextualWisdomLab/.github/.github/workflows/"
                        + "pr-review-merge-scheduler.yml@"
                        + "6eb06cdd08c79a06f7b390069d4ffa49e2eb7dba"
        ));
        assertTrue(workflow.contains("base_branch: develop"));
        assertTrue(workflow.contains("max_prs: \"100\""));
        assertTrue(workflow.contains("trigger_reviews: true"));
        assertTrue(workflow.contains("enable_auto_merge: true"));
        assertTrue(workflow.contains("merge_mode: direct_or_auto"));
        assertTrue(workflow.contains("update_branches: true"));
    }

    /** Verifies the caller grants only the capabilities required by the reusable scheduler. */
    @Test
    void grantsBoundedSchedulerPermissionsWithoutSecretInheritance() {
        assertTrue(workflow.contains("actions: write"));
        assertTrue(workflow.contains("checks: read"));
        assertTrue(workflow.contains("contents: write"));
        assertTrue(workflow.contains("id-token: write"));
        assertTrue(workflow.contains("pull-requests: write"));
        assertFalse(workflow.contains("secrets: inherit"));
        assertFalse(workflow.contains("COPILOT_GITHUB_TOKEN"));
        assertFalse(workflow.contains("NVIDIA_NIM_API_KEY"));
        assertFalse(workflow.contains("security-events: write"));
    }

    /** Verifies the previous local merger remains available only as a manual fail-closed fallback. */
    @Test
    void keepsLegacyDispositionAsManualOnlyFallback() {
        assertTrue(legacyDispositionWorkflow.contains("workflow_dispatch:"));
        assertFalse(legacyDispositionWorkflow.contains("schedule:"));
        assertFalse(legacyDispositionWorkflow.contains("cron: \"11 * * * *\""));
    }

    /** Verifies operators can identify the one scheduled authority and its manual fallback. */
    @Test
    void documentsCentralAuthorityAndManualFallback() {
        assertTrue(documentation.contains(".github/workflows/hourly-pr-maintenance.yml"));
        assertTrue(documentation.contains("minute 17"));
        assertTrue(documentation.contains("centrally governed"));
        assertTrue(documentation.contains(".github/workflows/hourly-pr-disposition.yml"));
        assertTrue(documentation.contains("manual fail-closed fallback"));
        assertTrue(documentation.contains("no duplicate scheduled merge authority"));
    }

    /** Verifies the maintenance-authority change is discoverable in release history. */
    @Test
    void recordsMaintenanceAuthorityChange() {
        assertTrue(changelog.contains("Hourly pull-request maintenance"));
        assertTrue(changelog.contains("manual fail-closed fallback"));
    }

    /**
     * Reads one UTF-8 repository artifact and normalizes platform line endings.
     *
     * @param path repository artifact to read
     * @return normalized UTF-8 text
     * @throws IOException when the artifact cannot be read
     */
    private static String readNormalized(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    /**
     * Finds the repository root from either reactor-root or module-local Maven execution.
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
