package com.xtrmetl.etl.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards repository-owned developer launch configuration against unsafe or conflicting
 * developer-runtime authority paths.
 */
class RepositoryRuntimeSupplyChainTest {

    private static final Path PROJECT_ROOT = projectRoot();
    private static final Pattern REMOTE_SHELL_PIPE = Pattern.compile(
            "(?im)\\bcurl\\b[^\\r\\n|]*\\|\\s*(?:bash|sh)\\b"
    );

    @Test
    void replitDoesNotExecuteTrackedZipkinJar() throws IOException {
        String replit = readReplit();
        assertFalse(
                replit.contains("java -jar zipkin.jar"),
                "Replit launch configuration must not execute the repository-tracked opaque Zipkin JAR"
        );
    }

    @Test
    void replitDoesNotPipeMutableRemoteContentIntoAShell() throws IOException {
        String replit = readReplit();
        assertFalse(
                REMOTE_SHELL_PIPE.matcher(replit).find(),
                "Replit launch configuration must not pipe mutable remote content directly into a shell"
        );
    }

    @Test
    void projectRunButtonDelegatesToOneMicroserviceTopology() throws IOException {
        String projectWorkflow = workflowBlock(readReplit(), "Project");
        int serviceTopologyDelegates = countOccurrences(projectWorkflow, "args = \"Run Microservices\"")
                + countOccurrences(projectWorkflow, "args = \"Run Eureka Server\"")
                + countOccurrences(projectWorkflow, "args = \"Build and Run Microservices\"");

        assertEquals(
                1,
                serviceTopologyDelegates,
                "The Replit run button must not launch overlapping copies of the same microservice topology"
        );
        assertTrue(
                projectWorkflow.contains("args = \"Run Microservices\""),
                "The Replit run button must delegate service startup to the canonical Run Microservices workflow"
        );
    }

    private static String readReplit() throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(".replit"), StandardCharsets.UTF_8);
    }

    private static String workflowBlock(String replit, String workflowName) {
        String marker = "[[workflows.workflow]]\nname = \"" + workflowName + "\"";
        int start = replit.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("Missing Replit workflow: " + workflowName);
        }
        int end = replit.indexOf("[[workflows.workflow]]", start + marker.length());
        return end < 0 ? replit.substring(start) : replit.substring(start, end);
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

    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve(".replit"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate mightyETL repository root");
    }
}
