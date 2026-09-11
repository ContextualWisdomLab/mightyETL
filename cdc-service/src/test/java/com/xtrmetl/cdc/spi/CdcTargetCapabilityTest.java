package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-first contract for truthful CDC target execution metadata.
 *
 * <p>The production target registry currently exposes live Kafka and JDBC-replica product paths
 * through connector objects whose canonical {@code write(...)} SPI is intentionally unwired. This
 * contract requires machine-readable capability metadata so discovery can distinguish the shipped
 * product path from canonical-write execution authority.</p>
 */
class CdcTargetCapabilityTest {

    @Test
    void liveTargetsDiscloseTheirActualExecutionBoundary() throws Exception {
        Method capabilitiesMethod = Arrays.stream(CdcTargetConnector.class.getMethods())
                .filter(method -> method.getName().equals("capabilities"))
                .findFirst()
                .orElse(null);

        assertTrue(capabilitiesMethod != null,
                "CDC target SPI must expose capabilities instead of overloading scaffoldOnly");

        assertCapabilities(
                capabilitiesMethod.invoke(new KafkaCdcTargetConnector()),
                "RAW_DEBEZIUM_KAFKA"
        );
        assertCapabilities(
                capabilitiesMethod.invoke(new JdbcReplicaCdcTargetConnector()),
                "PROCESSED_DATA_JDBC_REPLICA"
        );
    }

    private static void assertCapabilities(Object capabilities, String expectedDeliveryMode) throws Exception {
        Method productPathLive = capabilities.getClass().getMethod("productPathLive");
        Method canonicalWriteSupported = capabilities.getClass().getMethod("canonicalWriteSupported");
        Method deliveryMode = capabilities.getClass().getMethod("deliveryMode");

        assertTrue((Boolean) productPathLive.invoke(capabilities));
        assertFalse((Boolean) canonicalWriteSupported.invoke(capabilities));
        assertEquals(expectedDeliveryMode, ((Enum<?>) deliveryMode.invoke(capabilities)).name());
    }
}
