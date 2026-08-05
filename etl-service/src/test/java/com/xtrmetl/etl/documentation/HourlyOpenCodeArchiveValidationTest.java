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
 * Guards archive-member validation before the scheduled workflow extracts the OpenCode binary.
 *
 * <p>A checksum proves that downloaded bytes match the reviewed release asset, but it does not
 * independently constrain where archive members would be written. The workflow therefore accepts
 * only the documented one-file release shape: one regular member named {@code opencode}. This
 * contract prevents future tool-pin changes from silently broadening extraction to directories,
 * links, device nodes, absolute paths, parent-directory paths, or unexpected additional files.</p>
 */
class HourlyOpenCodeArchiveValidationTest {

    private static String workflow;

    /**
     * Reads the workflow as normalized UTF-8 text for deterministic assertions on every CI
     * operating system.
     *
     * @throws IOException when the workflow cannot be read
     */
    @BeforeAll
    static void readWorkflow() throws IOException {
        Path workflowPath = projectRoot().resolve(
                ".github/workflows/hourly-opencode-maintenance.yml"
        );
        workflow = Files.readString(workflowPath, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    /**
     * Requires exact archive membership to be checked before extraction begins.
     */
    @Test
    void validatesOneExpectedArchiveMemberBeforeExtraction() {
        String validation = "mapfile -t archive_members < <(tar --list --gzip --file \"${archive}\")";
        String exactShape = "[[ \"${#archive_members[@]}\" -ne 1 "
                + "|| \"${archive_members[0]}\" != \"opencode\" ]]";
        String extraction = "tar --extract --gzip --no-same-owner --no-same-permissions";

        assertTrue(workflow.contains(validation));
        assertTrue(workflow.contains(exactShape));
        assertTrue(workflow.contains("OpenCode archive contains unexpected members"));
        assertTrue(workflow.contains(extraction));
        assertTrue(workflow.indexOf(validation) < workflow.indexOf(extraction));
        assertTrue(workflow.indexOf(exactShape) < workflow.indexOf(extraction));
    }

    /**
     * Requires the sole member to be a regular-file entry before tar writes to the filesystem.
     *
     * <p>Post-extraction symbolic-link checks remain useful defense in depth, but they cannot by
     * themselves distinguish an ordinary archived file from every link-oriented archive entry.
     * GNU tar's verbose listing begins each member with its type character, so the Ubuntu-only
     * installer must require {@code -} for a regular file before extraction.</p>
     */
    @Test
    void validatesRegularFileEntryTypeBeforeExtraction() {
        String metadata = "archive_entry_metadata=\"$(LC_ALL=C tar --list --verbose "
                + "--numeric-owner --gzip --file \"${archive}\")\"";
        String regularType = "[[ \"${archive_entry_metadata:0:1}\" != \"-\" ]]";
        String extraction = "tar --extract --gzip --no-same-owner --no-same-permissions";

        assertTrue(workflow.contains(metadata));
        assertTrue(workflow.contains(regularType));
        assertTrue(workflow.contains("OpenCode archive member is not a regular file"));
        assertTrue(workflow.indexOf(metadata) < workflow.indexOf(extraction));
        assertTrue(workflow.indexOf(regularType) < workflow.indexOf(extraction));
    }

    /**
     * Finds the repository root from either root-level or module-local Maven execution.
     *
     * @return absolute repository root containing the Maven reactor
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
