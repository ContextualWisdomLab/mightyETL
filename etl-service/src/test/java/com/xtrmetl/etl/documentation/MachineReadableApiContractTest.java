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
 * Guards the checked-in machine-readable public HTTP and CDC event contracts against the exact
 * protected-develop product surface. Active pull-request routes must not be advertised early.
 */
class MachineReadableApiContractTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void openApiContractExistsAndDescribesOnlyProtectedHttpSurface() throws IOException {
        String openApi = read("contracts/openapi/mightyetl.yaml");

        assertTrue(openApi.contains("openapi: 3.2.0"));
        assertTrue(openApi.contains("/api/etl/process:"));
        assertTrue(openApi.contains("/api/etl/connectors:"));
        assertTrue(openApi.contains("/api/etl/jobs:"));
        assertTrue(openApi.contains("/api/etl/jobs/{jobRecordId}:"));
        assertTrue(openApi.contains("/api/cdc/start:"));
        assertTrue(openApi.contains("/api/cdc/stop:"));
        assertTrue(openApi.contains("/api/cdc/status:"));
        assertTrue(openApi.contains("/api/cdc/sources:"));
        assertTrue(openApi.contains("/api/cdc/targets:"));
        assertTrue(openApi.contains("application/problem+json"));
        assertTrue(openApi.contains("Idempotency-Key"));
        assertTrue(openApi.contains("Idempotency-Replayed"));
        assertTrue(openApi.contains("Location"));
        assertTrue(openApi.contains("Cache-Control"));

        assertFalse(openApi.contains("/api/etl/jobs/{jobRecordId}/cancellation:"));
        assertFalse(openApi.contains("/api/etl/jobs/{sourceJobRecordId}/replays:"));
        assertFalse(openApi.contains("Retry-After"));
        assertFalse(openApi.contains("If-None-Match"));
        assertFalse(openApi.contains("ETag"));
    }

    @Test
    void asyncApiContractExistsAndDescribesReplayTolerantKafkaCdc() throws IOException {
        String asyncApi = read("contracts/asyncapi/mightyetl-cdc.yaml");

        assertTrue(asyncApi.contains("asyncapi: 3.1.0"));
        assertTrue(asyncApi.contains("kafka"));
        assertTrue(asyncApi.contains("application/json"));
        assertTrue(asyncApi.contains("Debezium"));
        assertTrue(asyncApi.contains("at-least-once"));
        assertFalse(asyncApi.toLowerCase().contains("exactly-once"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

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
