package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void openApiRoutesRemainBoundToProtectedControllerSource() throws IOException {
        String openApi = read("contracts/openapi/mightyetl.yaml");
        String etlController = read(
                "etl-service/src/main/java/com/xtrmetl/etl/controller/EtlController.java"
        );
        String jobController = read(
                "etl-service/src/main/java/com/xtrmetl/etl/controller/EtlJobController.java"
        );
        String cdcController = read(
                "cdc-service/src/main/java/com/xtrmetl/cdc/controller/CdcController.java"
        );

        assertSourceRouteDocumented(etlController, openApi, "/api/etl/process");
        assertSourceRouteDocumented(etlController, openApi, "/api/etl/connectors");

        assertTrue(jobController.contains("@RequestMapping(\"/api/etl/jobs\")"));
        assertTrue(jobController.contains("@PostMapping"));
        assertTrue(jobController.contains("@GetMapping(\"/{jobRecordId}\")"));
        assertTrue(openApi.contains("/api/etl/jobs:"));
        assertTrue(openApi.contains("/api/etl/jobs/{jobRecordId}:"));

        assertTrue(cdcController.contains("@RequestMapping(\"/api/cdc\")"));
        assertChildRouteDocumented(cdcController, openApi, "/start", "/api/cdc/start:");
        assertChildRouteDocumented(cdcController, openApi, "/stop", "/api/cdc/stop:");
        assertChildRouteDocumented(cdcController, openApi, "/status", "/api/cdc/status:");
        assertChildRouteDocumented(cdcController, openApi, "/sources", "/api/cdc/sources:");
        assertChildRouteDocumented(cdcController, openApi, "/targets", "/api/cdc/targets:");
    }

    @Test
    void protectedEtlOperationsDeclareHttpBasicAuthenticationFailures() throws IOException {
        String openApi = read("contracts/openapi/mightyetl.yaml");
        String securityConfig = read(
                "etl-service/src/main/java/com/xtrmetl/etl/security/SecurityConfig.java"
        );

        assertTrue(securityConfig.contains(".requestMatchers(\"/api/**\").authenticated()"));
        assertTrue(securityConfig.contains(".httpBasic(Customizer.withDefaults())"));
        assertEquals(
                4,
                countOccurrences(
                        openApi,
                        "'401':\n          $ref: '#/components/responses/Unauthorized'"
                ),
                "Every protected ETL operation must declare the source-backed 401 surface"
        );
        assertTrue(openApi.contains("    Unauthorized:\n"));
        assertTrue(openApi.contains("WWW-Authenticate:"));
    }

    @Test
    void asyncApiContractExistsAndDescribesReplayTolerantKafkaCdc() throws IOException {
        String asyncApi = read("contracts/asyncapi/mightyetl-cdc.yaml");
        String cdcService = read("cdc-service/src/main/java/com/xtrmetl/cdc/service/CdcService.java");

        assertTrue(asyncApi.contains("asyncapi: 3.1.0"));
        assertTrue(asyncApi.contains("kafka"));
        assertTrue(asyncApi.contains("application/json"));
        assertTrue(asyncApi.contains("Debezium"));
        assertTrue(asyncApi.contains("at-least-once"));
        assertFalse(asyncApi.toLowerCase().contains("exactly-once"));

        assertTrue(cdcService.contains("String topic = changeEvent.destination();"));
        assertTrue(cdcService.contains("kafkaTemplate.send(topic, key, value)"));
        assertTrue(cdcService.contains("kafkaTemplate.send(topic, value)"));
        assertTrue(asyncApi.contains("address: '{destination}'"));
        assertTrue(asyncApi.contains("raw Debezium JSON"));
    }

    private static void assertSourceRouteDocumented(String source, String openApi, String route) {
        assertTrue(source.contains(route), () -> "production source no longer declares route " + route);
        assertTrue(openApi.contains(route + ":"), () -> "OpenAPI missing production route " + route);
    }

    private static void assertChildRouteDocumented(
            String source,
            String openApi,
            String childRoute,
            String openApiRoute
    ) {
        assertTrue(
                source.contains("(\"" + childRoute + "\")"),
                () -> "CDC controller no longer declares child route " + childRoute
        );
        assertTrue(openApi.contains(openApiRoute), () -> "OpenAPI missing route " + openApiRoute);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int position = 0;
        while ((position = text.indexOf(needle, position)) >= 0) {
            count++;
            position += needle.length();
        }
        return count;
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
