package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(first));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(second)
        );

        assertEquals("Duplicate CDC source connector id: duplicate-source", failure.getMessage());
        assertSame(first, registry.find("duplicate-source").orElseThrow());
    }

    @Test
    void duplicateTargetConnectorIdsFailClosedInsteadOfReplacingRegistration() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        CdcTargetConnector originalKafka = registry.find(KafkaCdcTargetConnector.ID).orElseThrow();
        CdcTargetConnector duplicateKafka = target(KafkaCdcTargetConnector.ID);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(duplicateKafka)
        );

        assertEquals("Duplicate CDC target connector id: kafka", failure.getMessage());
        assertSame(originalKafka, registry.find(KafkaCdcTargetConnector.ID).orElseThrow());
    }

    @Test
    void springDiscoveryConstructorIsExplicitlyAutowired() {
        Constructor<?> discoveryConstructor = Arrays.stream(CdcSourceRegistry.class.getConstructors())
                .filter(constructor -> Arrays.equals(
                        constructor.getParameterTypes(),
                        new Class<?>[]{ObjectProvider.class}
                ))
                .findFirst()
                .orElseThrow();

        assertTrue(discoveryConstructor.isAnnotationPresent(Autowired.class),
                "Spring discovery constructor must be explicitly selected when other public constructors exist");
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

    @Test
    void sourceConnectorCollectionCannotRemoveRegisteredConnector() {
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(source("immutable-source")));
        int registeredBefore = registry.all().size();
        CdcSourceConnector retained = registry.find("immutable-source").orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> registry.all().remove(retained));

        assertSame(retained, registry.find("immutable-source").orElseThrow());
        assertEquals(registeredBefore, registry.all().size());
    }

    @Test
    void targetConnectorCollectionCannotRemoveRegisteredConnector() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        int registeredBefore = registry.all().size();
        CdcTargetConnector kafka = registry.find(KafkaCdcTargetConnector.ID).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> registry.all().remove(kafka));

        assertSame(kafka, registry.find(KafkaCdcTargetConnector.ID).orElseThrow());
        assertEquals(registeredBefore, registry.all().size());
    }

    @Test
    void sourceConnectorIteratorCannotRemoveRegisteredConnector() {
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(source("immutable-source")));
        int registeredBefore = registry.all().size();
        Iterator<CdcSourceConnector> iterator = registry.all().iterator();
        assertTrue(iterator.hasNext());
        iterator.next();

        assertThrows(UnsupportedOperationException.class, iterator::remove);

        assertEquals(registeredBefore, registry.all().size());
        assertTrue(registry.find("immutable-source").isPresent());
    }

    @Test
    void targetConnectorIteratorCannotRemoveRegisteredConnector() {
        CdcTargetRegistry registry = new CdcTargetRegistry();
        int registeredBefore = registry.all().size();
        Iterator<CdcTargetConnector> iterator = registry.all().iterator();
        assertTrue(iterator.hasNext());
        iterator.next();

        assertThrows(UnsupportedOperationException.class, iterator::remove);

        assertEquals(registeredBefore, registry.all().size());
        assertTrue(registry.find(KafkaCdcTargetConnector.ID).isPresent());
    }

    @Test
    void returnedSourceConnectorSnapshotDoesNotChangeAfterLaterRegistration() {
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(source("immutable-source")));
        Collection<CdcSourceConnector> snapshot = registry.all();
        int snapshotSize = snapshot.size();

        CdcSourceConnector laterConnector = source("later-source");
        registry.register(laterConnector);

        assertEquals(snapshotSize, snapshot.size());
        assertTrue(registry.find("later-source").isPresent());
        assertFalse(snapshot.contains(laterConnector));
    }

    @Test
    void sourceConnectorSnapshotPreservesRegistrationOrderAndIdentity() {
        CdcSourceConnector first = source("snapshot-first");
        CdcSourceConnector second = source("snapshot-second");
        CdcSourceRegistry registry = new CdcSourceRegistry(List.of(first, second));

        List<String> ids = registry.all().stream().map(CdcSourceConnector::id).toList();

        assertEquals(List.of("snapshot-first", "snapshot-second"), ids);
        assertSame(first, registry.find("snapshot-first").orElseThrow());
        assertSame(second, registry.find("snapshot-second").orElseThrow());
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
