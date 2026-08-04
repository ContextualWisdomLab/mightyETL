package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the idempotency runbook honest about failures rejected before controller invocation.
 */
class EtlIdempotencyAuthenticationBoundaryDocumentationTest {

    @Test
    void distinguishesSecurityLayerRejectionFromTheControllerPrincipalGuard() throws IOException {
        String runbook = Files.readString(
                projectRoot().resolve("docs/etl/idempotent-retries.md"),
                StandardCharsets.UTF_8
        );

        assertTrue(runbook.contains("rejected before the ETL controller"));
        assertTrue(runbook.contains("may not carry `etl_idempotency_principal_required`"));
        assertTrue(runbook.contains("reaches the controller without a principal"));
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
