package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that target-connector lifecycle logs retain finite outcome classification without
 * serializing third-party exception diagnostics.
 */
@ExtendWith(OutputCaptureExtension.class)
class TargetConnectorDispatcherLoggingTest {

    @Test
    void failedOpenCleanupDoesNotLogProviderDiagnostics(CapturedOutput output) {
        String openSecret = "https://warehouse.example/sql?token=open-secret-8472";
        String cleanupSecret = "cleanup-secret-8472";
        RuntimeException openFailure = new IllegalStateException(openSecret);
        RuntimeException cleanupFailure = new IllegalArgumentException(cleanupSecret);
        FailingLifecycleConnector connector = new FailingLifecycleConnector(openFailure, cleanupFailure, false);
        TargetConnectorDispatcher dispatcher = dispatcher(connector);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> dispatcher.dispatch("databricks", List.of())
        );

        assertSame(openFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
        assertSafeLogs(
                output,
                "Failed to clean up target connector after open failure id=databricks",
                cleanupSecret,
                "IllegalArgumentException",
                "TargetConnectorDispatcherLoggingTest"
        );
    }

    @Test
    void shutdownCloseFailureDoesNotLogProviderDiagnosticsAndRemainsBestEffort(CapturedOutput output) {
        String closeSecret = "jdbc:vendor://internal.example/prod?password=close-secret-8472";
        FailingLifecycleConnector connector = new FailingLifecycleConnector(
                null,
                new IllegalStateException(closeSecret),
                true
        );
        TargetConnectorDispatcher dispatcher = dispatcher(connector);

        dispatcher.dispatch("databricks", List.of());
        dispatcher.closeOpenedConnectors();

        assertEquals(List.of("open", "write", "close"), connector.events);
        assertSafeLogs(
                output,
                "Failed to close target connector id=databricks",
                closeSecret,
                "IllegalStateException",
                "TargetConnectorDispatcherLoggingTest"
        );
        assertFalse(Boolean.TRUE.equals(dispatcher.catalog().stream()
                .filter(row -> "databricks".equals(row.get("id")))
                .findFirst()
                .orElseThrow()
                .get("opened")));
    }

    private static TargetConnectorDispatcher dispatcher(TargetConnector connector) {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        return new TargetConnectorDispatcher(registry, enabledDatabricksProperties());
    }

    private static ConnectorProperties enabledDatabricksProperties() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.getDatabricks().setEnabled(true);
        properties.getDatabricks().setHost("host");
        properties.getDatabricks().setHttpPath("/sql");
        properties.getDatabricks().setToken("token");
        properties.getDatabricks().setCatalog("catalog");
        properties.getDatabricks().setSchema("schema");
        properties.getDatabricks().setTable("table_name");
        return properties;
    }

    private static void assertSafeLogs(CapturedOutput output, String expected, String... forbidden) {
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains(expected));
        for (String value : forbidden) {
            assertFalse(logs.contains(value), () -> "Log output exposed forbidden value: " + value);
        }
    }

    private static final class FailingLifecycleConnector implements TargetConnector {
        private final RuntimeException openFailure;
        private final RuntimeException closeFailure;
        private final boolean openSucceeds;
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();

        private FailingLifecycleConnector(
                RuntimeException openFailure,
                RuntimeException closeFailure,
                boolean openSucceeds
        ) {
            this.openFailure = openFailure;
            this.closeFailure = closeFailure;
            this.openSucceeds = openSucceeds;
        }

        @Override
        public String id() {
            return "databricks";
        }

        @Override
        public String displayName() {
            return "Failing lifecycle connector";
        }

        @Override
        public ConnectorStatus status() {
            return ConnectorStatus.SUPPORTED;
        }

        @Override
        public void validate(Map<String, String> config) {
            // This fake intentionally accepts the validated dispatcher fixture configuration.
        }

        @Override
        public void open(Map<String, String> config) {
            events.add("open");
            if (!openSucceeds) {
                throw openFailure;
            }
        }

        @Override
        public void write(List<ChangeRecord> batch) {
            events.add("write");
        }

        @Override
        public void close() {
            events.add("close");
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
