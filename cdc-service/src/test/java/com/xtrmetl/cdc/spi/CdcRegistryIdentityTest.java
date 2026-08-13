package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fail-first contract for CDC connector registration authority.
 *
 * <p>Connector identifiers select production implementations. Invalid registration must fail before
 * registry mutation so bean order or plugin code cannot silently replace or remove that authority.</p>
 */
class CdcRegistryIdentityTest {

    @Test
    void duplicateSourceConnectorIdsFailClosedInsteadOfReplacingRegistration() {
        CdcSourceConnector first = source("duplicate-source");
        CdcSourceConnector second = source("duplicate-source");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new CdcSourceRegistry(List.of(first, second))
        );

        assertEquals("Duplicate CDC source connector id: duplicate-source", failure.getMessage());
    }

    @Test
    void duplicateTargetConnectorIdsFailClosedInsteadOfReplacingRegistration() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        CdcTargetConnector duplicateKafka = target(KafkaCdcTargetConnector.ID);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(duplicateKafka)
        );

        assertEquals("Duplicate CDC target connector id: kafka", failure.getMessage());
    }

    @Test
    void nullSourceConnectorFailsBeforeRegistryMutation() {
        CdcSourceRegistry registry = new CdcSourceRegistry();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        assertEquals("CDC source connector must not be null", failure.getMessage());
    }

    @Test
    void nullTargetConnectorFailsBeforeRegistryMutation() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        assertEquals("CDC target connector must not be null", failure.getMessage());
    }

    @Test
    void blankSourceConnectorIdFailsBeforeRegistryMutation() {
        CdcSourceConnector blank = source("   ");
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new CdcSourceRegistry(List.of(blank))
        );
        assertEquals("CDC source connector id must not be blank", failure.getMessage());
    }

    @Test
    void blankTargetConnectorIdFailsBeforeRegistryMutation() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        CdcTargetConnector blank = target("");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> registry.register(blank));
        assertEquals("CDC target connector id must not be blank", failure.getMessage());
    }

    @Test
    void sourceConnectorCollectionCannotDeleteRegistrationAuthority() {
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(source("immutable-source")));
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
        assertTrue(registry.find("immutable-source").isPresent());
    }

    @Test
    void targetConnectorCollectionCannotDeleteRegistrationAuthority() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
        assertTrue(registry.find(KafkaCdcTargetConnector.ID).isPresent());
        assertTrue(registry.find(JdbcReplicaCdcTargetConnector.ID).isPresent());
    }

    private static CdcSourceConnector source(String id) {
        CdcSourceConnector connector = mock(CdcSourceConnector.class);
        when(connector.id()).thenReturn(id);
        return connector;
    }

    private static CdcTargetConnector target(String id) {
        CdcTargetConnector connector = mock(CdcTargetConnector.class);
        when(connector.id()).thenReturn(id);
        return connector;
    }
}
