package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that supported target connectors cannot bypass dispatcher-owned configuration
 * validation by overriding their {@link TargetConnector#open(Map)} implementation.
 */
class TargetConnectorDispatcherValidationTest {

    @Test
    void validatesSupportedConnectorBeforeOpening() {
        ValidationProbeConnector connector = new ValidationProbeConnector();
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        registry.register(connector);
        TargetConnectorDispatcher dispatcher = new TargetConnectorDispatcher(
                registry,
                enabledDatabricksProperties()
        );

        dispatcher.dispatch("databricks", List.of());

        assertEquals(List.of("validate", "open", "write"), connector.events);
        dispatcher.closeOpenedConnectors();
    }

    private static ConnectorProperties enabledDatabricksProperties() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.getDatabricks().setEnabled(true);
        properties.getDatabricks().setHost("host");
        properties.getDatabricks().setHttpPath("/sql");
        properties.getDatabricks().setToken("token");
        properties.getDatabricks().setCatalog("catalog");
        properties.getDatabricks().setSchema("schema");
        properties.getDatabricks().setTable("table");
        return properties;
    }

    private static final class ValidationProbeConnector implements TargetConnector {
        private final List<String> events = new ArrayList<>();

        @Override
        public String id() {
            return "databricks";
        }

        @Override
        public String displayName() {
            return "Validation probe";
        }

        @Override
        public ConnectorStatus status() {
            return ConnectorStatus.SUPPORTED;
        }

        @Override
        public void validate(Map<String, String> config) {
            events.add("validate");
        }

        @Override
        public void open(Map<String, String> config) {
            events.add("open");
        }

        @Override
        public void write(List<ChangeRecord> batch) {
            events.add("write");
        }
    }
}
