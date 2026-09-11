package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests on warehouse target connectors intentionally exposed by the production registry.
 */
class WarehouseBiConnectorSurfaceTest {

    static Stream<TargetConnector> scaffolds() {
        return Stream.of(
                new DatabricksTargetConnector(),
                new SnowflakeTargetConnector()
        );
    }

    @ParameterizedTest
    @MethodSource("scaffolds")
    void reportsScaffoldAndRefusesWrite(TargetConnector connector) {
        assertEquals(ConnectorStatus.SCAFFOLD, connector.status());
        assertFalse(connector.requiredConfigKeys().isEmpty());
        assertTrue(connector.writeRefusalReason().contains("SCAFFOLD"));
        assertEquals(false, connector.describeIntegration().get("driverOnClasspath"));
        assertThrows(UnsupportedOperationException.class, () -> connector.write(List.of()));
    }

    @ParameterizedTest
    @MethodSource("scaffolds")
    void validateFailsOnMissingRequiredKeys(TargetConnector connector) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> connector.validate(Map.of())
        );
        assertTrue(ex.getMessage().contains("missing required keys"));
        for (String key : connector.requiredConfigKeys()) {
            assertTrue(ex.getMessage().contains(key), "message should list " + key);
        }
    }

    @ParameterizedTest
    @MethodSource("scaffolds")
    void validateAndOpenAcceptCompleteConfigButWriteStillRefused(TargetConnector connector) {
        Map<String, String> config = completeConfig(connector);
        connector.validate(config);
        connector.open(config);
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> connector.write(List.of(
                        new ChangeRecord("src", "c", "public", "t", 1L, Map.of(), Map.of("id", 1), Map.of("id", 1))
                ))
        );
        assertTrue(ex.getMessage().contains("SCAFFOLD"));
    }

    @Test
    void databricksRequiresWarehouseKeys() {
        DatabricksTargetConnector c = new DatabricksTargetConnector();
        assertEquals("databricks", c.id());
        assertTrue(c.requiredConfigKeys().containsAll(
                List.of("host", "http-path", "token", "catalog", "schema", "table")));
        assertTrue(c.optionalConfigKeys().contains("write-mode"));
    }

    @Test
    void snowflakeRequiresAccountPathKeys() {
        SnowflakeTargetConnector c = new SnowflakeTargetConnector();
        assertEquals("snowflake", c.id());
        assertTrue(c.requiredConfigKeys().containsAll(
                List.of("account", "warehouse", "database", "schema", "user", "table")));
    }

    @Test
    void qlikIsNotPublishedAsARowWriteTargetUntilItsRealProductBoundaryExists() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();

        assertTrue(registry.find("qlik-sense").isEmpty());
        assertTrue(registry.all().stream().noneMatch(connector -> connector.id().equals("qlik-sense")));
    }

    @Test
    void propertiesConfigMapFeedsValidation() {
        ConnectorProperties props = new ConnectorProperties();
        props.getDatabricks().setEnabled(true);
        props.getDatabricks().setHost("dbc.example.com");
        props.getDatabricks().setHttpPath("/sql/1.0/warehouses/abc");
        props.getDatabricks().setToken("dapi-test");
        props.getDatabricks().setCatalog("main");
        props.getDatabricks().setSchema("default");
        props.getDatabricks().setTable("events");

        Map<String, String> map = props.configMap("databricks");
        new DatabricksTargetConnector().validate(map);
        assertEquals("dbc.example.com", map.get("host"));
        assertEquals("/sql/1.0/warehouses/abc", map.get("http-path"));
    }

    @Test
    void dispatcherValidatesBoundConfigBeforeScaffoldRefusal() {
        ConnectorProperties props = new ConnectorProperties();
        props.getSnowflake().setEnabled(true);
        // missing required keys
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), props);

        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("snowflake", List.of()));

        props.getSnowflake().setAccount("xy12345");
        props.getSnowflake().setWarehouse("COMPUTE_WH");
        props.getSnowflake().setDatabase("ANALYTICS");
        props.getSnowflake().setSchema("PUBLIC");
        props.getSnowflake().setUser("loader");
        props.getSnowflake().setTable("EVENTS");

        UnsupportedOperationException writeEx = assertThrows(
                UnsupportedOperationException.class,
                () -> dispatcher.dispatch("snowflake", List.of())
        );
        assertTrue(writeEx.getMessage().contains("SCAFFOLD"));
    }

    @Test
    void catalogExposesOnlyIntentionalWarehouseScaffolds() {
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), new ConnectorProperties());

        List<Map<String, Object>> catalog = dispatcher.catalog();
        assertEquals(2, catalog.size());
        assertTrue(catalog.stream().noneMatch(row -> "qlik-sense".equals(row.get("id"))));
        for (Map<String, Object> row : catalog) {
            assertEquals("SCAFFOLD", row.get("status"));
            assertEquals(false, row.get("writable"));
            assertTrue(row.get("requiredConfigKeys") instanceof List);
            assertFalse(((List<?>) row.get("requiredConfigKeys")).isEmpty());
            assertTrue(row.get("writeRefusalReason").toString().contains("SCAFFOLD"));
            assertTrue(row.get("integration") instanceof Map);
        }
    }

    private static Map<String, String> completeConfig(TargetConnector connector) {
        Map<String, String> config = new HashMap<>();
        for (String key : connector.requiredConfigKeys()) {
            config.put(key, "test-" + key);
        }
        for (String key : connector.optionalConfigKeys()) {
            config.put(key, "opt-" + key);
        }
        return config;
    }
}
