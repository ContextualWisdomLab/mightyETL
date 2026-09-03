package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CdcSourceConnector firstSourceConnector = sourceConnector("duplicate-source");
        CdcSourceConnector secondSourceConnector = sourceConnector("duplicate-source");
        CdcSourceRegistry sourceRegistry = new CdcSourceRegistry(List.of(firstSourceConnector));

        IllegalArgumentException registrationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> sourceRegistry.register(secondSourceConnector)
        );

        assertEquals("Duplicate CDC source connector id: duplicate-source", registrationFailure.getMessage());
        assertSame(firstSourceConnector, sourceRegistry.find("duplicate-source").orElseThrow());
    }

    @Test
    void duplicateTargetConnectorIdsFailClosedInsteadOfReplacingRegistration() {
        CdcTargetRegistry targetRegistry = new CdcTargetRegistry();
        CdcTargetConnector originalKafkaTarget = targetRegistry.find(KafkaCdcTargetConnector.TARGET_ID).orElseThrow();
        CdcTargetConnector duplicateKafkaTarget = targetConnector(KafkaCdcTargetConnector.TARGET_ID);

        IllegalArgumentException registrationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> targetRegistry.register(duplicateKafkaTarget)
        );

        assertEquals("Duplicate CDC target connector id: kafka", registrationFailure.getMessage());
        assertSame(originalKafkaTarget, targetRegistry.find(KafkaCdcTargetConnector.TARGET_ID).orElseThrow());
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
        CdcSourceRegistry sourceRegistry = new CdcSourceRegistry();
        IllegalArgumentException registrationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> sourceRegistry.register(null)
        );
        assertEquals("CDC source connector must not be null", registrationFailure.getMessage());
    }

    @Test
    void nullTargetConnectorFailsBeforeRegistryMutation() {
        CdcTargetRegistry targetRegistry = new CdcTargetRegistry();
        IllegalArgumentException registrationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> targetRegistry.register(null)
        );
        assertEquals("CDC target connector must not be null", registrationFailure.getMessage());
    }

    @Test
    void blankSourceConnectorIdFailsBeforeRegistryMutation() {
        CdcSourceConnector blankSourceConnector = sourceConnector("   ");
        IllegalArgumentException registrationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new CdcSourceRegistry(List.of(blankSourceConnector))
        );
        assertEquals("CDC source connector id must not be blank", registrationFailure.getMessage());
    }

    @Test
    void blankTargetConnectorIdFailsBeforeRegistryMutation() {
        CdcTargetRegistry targetRegistry = new CdcTargetRegistry();
        CdcTargetConnector blankTargetConnector = targetConnector("");
        IllegalArgumentException registrationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> targetRegistry.register(blankTargetConnector)
        );
        assertEquals("CDC target connector id must not be blank", registrationFailure.getMessage());
    }

    @Test
    void sourceConnectorCollectionCannotDeleteRegistrationAuthority() {
        CdcSourceRegistry sourceRegistry = new CdcSourceRegistry(List.of(sourceConnector("immutable-source")));
        assertThrows(UnsupportedOperationException.class, () -> sourceRegistry.all().clear());
        assertTrue(sourceRegistry.find("immutable-source").isPresent());
    }

    @Test
    void targetConnectorCollectionCannotDeleteRegistrationAuthority() {
        CdcTargetRegistry targetRegistry = new CdcTargetRegistry();
        assertThrows(UnsupportedOperationException.class, () -> targetRegistry.all().clear());
        assertTrue(targetRegistry.find(KafkaCdcTargetConnector.TARGET_ID).isPresent());
        assertTrue(targetRegistry.find(JdbcReplicaCdcTargetConnector.TARGET_ID).isPresent());
    }

    private static CdcSourceConnector sourceConnector(String sourceId) {
        CdcSourceConnector sourceConnector = mock(CdcSourceConnector.class);
        when(sourceConnector.sourceId()).thenReturn(sourceId);
        return sourceConnector;
    }

    private static CdcTargetConnector targetConnector(String targetId) {
        CdcTargetConnector targetConnector = mock(CdcTargetConnector.class);
        when(targetConnector.targetId()).thenReturn(targetId);
        return targetConnector;
    }
}
