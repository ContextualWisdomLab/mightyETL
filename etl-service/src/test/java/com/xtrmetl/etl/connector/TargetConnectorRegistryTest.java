package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetConnectorRegistryTest {

    @Test
    void registersOnlyAdvertisedWarehouseScaffolds() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();

        assertEquals(2, registry.all().size());
        assertTrue(registry.find("databricks").isPresent());
        assertTrue(registry.find("snowflake").isPresent());
        assertFalse(registry.find("qlik-sense").isPresent());

        for (TargetConnector connector : registry.all()) {
            assertEquals(ConnectorStatus.SCAFFOLD, connector.status());
            assertFalse(connector.requiredConfigKeys().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> connector.validate(Map.of()));
            assertThrows(UnsupportedOperationException.class,
                    () -> connector.write(List.of()));
        }
    }
}
