package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void discoveryCollectionCannotDeleteRegistrationAuthorityViaClear() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        int registeredBefore = registry.all().size();

        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());

        assertTrue(registry.find("databricks").isPresent());
        assertTrue(registry.find("snowflake").isPresent());
        assertEquals(registeredBefore, registry.all().size());
    }

    @Test
    void discoveryCollectionCannotRemoveRegistrationOfAConnector() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        int registeredBefore = registry.all().size();
        TargetConnector databricks = registry.find("databricks").orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> registry.all().remove(databricks));

        assertSame(databricks, registry.find("databricks").orElseThrow());
        assertEquals(registeredBefore, registry.all().size());
    }

    @Test
    void discoveryIteratorCannotRemoveRegistrationOfAConnector() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        int registeredBefore = registry.all().size();
        Iterator<TargetConnector> iterator = registry.all().iterator();
        assertTrue(iterator.hasNext());
        iterator.next();

        assertThrows(UnsupportedOperationException.class, iterator::remove);

        assertEquals(registeredBefore, registry.all().size());
    }

    @Test
    void previouslyReturnedDiscoverySnapshotDoesNotChangeAfterLaterRegistration() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();
        Collection<TargetConnector> snapshot = registry.all();
        int registeredBefore = snapshot.size();

        TargetConnector laterConnector = connector("later-target");
        registry.register(laterConnector);

        assertEquals(registeredBefore, snapshot.size());
        assertTrue(registry.find("later-target").isPresent());
        assertFalse(snapshot.contains(laterConnector));
    }

    @Test
    void discoveryCollectionPreservesRegistrationOrderAndConnectorIdentity() {
        TargetConnectorRegistry registry = new TargetConnectorRegistry();

        List<String> ids = new ArrayList<>();
        for (TargetConnector connector : registry.all()) {
            ids.add(connector.id());
        }

        assertEquals(List.of("databricks", "snowflake"), ids);
        for (String id : ids) {
            TargetConnector fromSnapshot = registry.all().stream()
                    .filter(connector -> connector.id().equals(id))
                    .findFirst()
                    .orElseThrow();
            assertSame(registry.find(id).orElseThrow(), fromSnapshot);
        }
    }

    private static TargetConnector connector(String id) {
        TargetConnector connector = mock(TargetConnector.class);
        when(connector.id()).thenReturn(id);
        return connector;
    }
}
