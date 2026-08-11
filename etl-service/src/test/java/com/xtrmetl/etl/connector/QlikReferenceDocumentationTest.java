package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the retained Qlik reference type aligned with its retired production lifecycle status.
 */
class QlikReferenceDocumentationTest {

    @Test
    void retainedReferenceTypeDoesNotClaimShippedProductionHooks() throws IOException {
        String source = Files.readString(
                Path.of(
                        "src/main/java/com/xtrmetl/etl/connector/",
                        "QlikSenseTargetConnector.java"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Reference-only design type"));
        assertTrue(source.contains("not registered in the production target registry"));
        assertFalse(source.contains("Ships config contract + validation + catalog hooks"));
    }
}
