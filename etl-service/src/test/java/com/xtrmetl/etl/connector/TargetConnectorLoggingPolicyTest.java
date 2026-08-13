package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the target-connector lifecycle logging boundary against publishing provider exceptions.
 */
class TargetConnectorLoggingPolicyTest {

    @Test
    void lifecycleFailureLogsDoNotCarryExceptionObjects() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/xtrmetl/etl/connector/TargetConnectorDispatcher.java"),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("connectorId,\n                        cleanupFailure"));
        assertFalse(source.contains("connectorId, exception"));
    }
}
