package com.xtrmetl.etl.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards repository-owned developer launch configuration against executing opaque bundled
 * observability binaries or mutable remote shell bootstrap content.
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

    private static String readReplit() throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(".replit"), StandardCharsets.UTF_8);
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
