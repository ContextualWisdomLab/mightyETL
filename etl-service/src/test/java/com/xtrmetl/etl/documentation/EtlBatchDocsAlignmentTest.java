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
 * Keeps operator-facing ETL safety claims aligned with the shipped configuration and runtime.
 */
class EtlBatchDocsAlignmentTest {

    @Test
    void readmeDescribesTheCurrentSynchronousTransactionalContract() throws IOException {
        String readme = read("README.md");

        assertTrue(readme.contains("Bounded, fully prevalidated, transaction-scoped request batches"));
        assertTrue(readme.contains("ETL_MAX_PAYLOAD_BYTES"));
        assertTrue(readme.contains("ETL_MAX_BATCH_RECORDS"));
        assertTrue(readme.contains("Retry limited to transient database failures"));
        assertFalse(readme.contains("Parallel record processing"));
        assertFalse(readme.contains("Automatic retry on failures"));
    }

    @Test
    void runbookStatesAdmissionRollbackAndIngressBoundaries() throws IOException {
        String runbook = read("docs/etl/bounded-atomic-batches.md");

        assertTrue(runbook.contains("A rejected request performs no database writes"));
        assertTrue(runbook.contains("rolls back earlier writes"));
        assertTrue(runbook.contains("not a substitute for edge enforcement"));
        assertTrue(runbook.contains("numeric JSON identifier types are rejected"));
    }

    @Test
    void configurationAndChangelogUseTheSameLimits() throws IOException {
        String application = read("etl-service/src/main/resources/application.yml");
        String changelog = read("CHANGELOG.md");

        assertTrue(application.contains("max-payload-bytes: ${ETL_MAX_PAYLOAD_BYTES:1048576}"));
        assertTrue(application.contains("max-batch-records: ${ETL_MAX_BATCH_RECORDS:1000}"));
        assertTrue(changelog.contains("docs/etl/bounded-atomic-batches.md"));
        assertTrue(changelog.contains("retry only transient Spring data-access failures"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * Finds the reactor root from either root or module-local Maven execution.
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
