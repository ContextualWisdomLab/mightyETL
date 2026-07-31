package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetConnectorRegistryTest {

    @Test
    void registersScaffoldConnectorsForWarehouseAndBiTargets() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();

        assertEquals(3, registry.all().size());
        assertTrue(registry.find("databricks").isPresent());
        assertTrue(registry.find("snowflake").isPresent());
        assertTrue(registry.find("qlik-sense").isPresent());

        for (TargetConnector connector : registry.all()) {
            assertEquals(ConnectorStatus.SCAFFOLD, connector.status());
            connector.validate(Map.of());
            assertThrows(UnsupportedOperationException.class,
                    () -> connector.write(List.of()));
        }
    }
}
