package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps operator documentation aligned with the bounded ETL processing contract.
 */
class EtlBatchAdmissionDocumentationTest {

    @Test
    void runbookDocumentsLimitsBackpressureAndAtomicityBoundary() throws IOException {
        String runbook = read("docs/etl/batch-admission-and-backpressure.md");

        assertTrue(runbook.contains("mightyetl.etl.max-batch-records"));
        assertTrue(runbook.contains("mightyetl.etl.max-concurrency"));
        assertTrue(runbook.contains("mightyetl.etl.queue-capacity"));
        assertTrue(runbook.contains("caller-runs backpressure"));
        assertTrue(runbook.contains("does **not** provide atomic batch commits"));
        assertTrue(runbook.contains("A rejected request performs no database writes"));
    }

    @Test
    void serviceConfigurationListsShortEnvironmentAliases() throws IOException {
        String application = read("etl-service/src/main/resources/application.yml");

        assertTrue(application.contains("ETL_MAX_BATCH_RECORDS"));
        assertTrue(application.contains("ETL_MAX_CONCURRENCY"));
        assertTrue(application.contains("ETL_QUEUE_CAPACITY"));
        assertTrue(application.contains("max-batch-records: ${ETL_MAX_BATCH_RECORDS:1000}"));
        assertTrue(application.contains("queue-capacity: ${ETL_QUEUE_CAPACITY:1024}"));
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
