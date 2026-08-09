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
 * Verifies that the bundled Docker Compose tracing path targets Zipkin's documented HTTP port.
 */
class DockerComposeZipkinTransportTest {

    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final String INTERNAL_ZIPKIN_ENDPOINT =
            "MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: http://zipkin:9411/api/v2/spans";

    @Test
    void bundledServicesTargetTheStandardZipkinCollectorPort() throws IOException {
        String compose = Files.readString(
                PROJECT_ROOT.resolve("docker-compose.yml"),
                StandardCharsets.UTF_8
        );

        assertTrue(compose.contains("image: openzipkin/zipkin:2"));
        assertTrue(
                compose.contains("- \"9412:9411\""),
                "The compatibility host port must forward to Zipkin's documented container port 9411"
        );
        assertFalse(
                compose.contains("- \"9412:9412\""),
                "The bundled image must not be treated as listening on undocumented container port 9412"
        );
        assertEquals(
                3,
                countOccurrences(compose, INTERNAL_ZIPKIN_ENDPOINT),
                "ETL, CDC, and gateway must all export traces to Zipkin port 9411 inside Compose"
        );
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = text.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }

    private static Path findProjectRoot() {
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
        throw new IllegalStateException("Could not find project root (no .git or pom.xml found)");
    }
}
