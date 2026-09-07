package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdcTargetRegistryTest {

    @Test
    void registersKafkaAndJdbcReplicaTargets() {
        CdcTargetRegistry targetRegistry = new CdcTargetRegistry();

        assertEquals(2, targetRegistry.all().size());
        assertTrue(targetRegistry.find(KafkaCdcTargetConnector.TARGET_ID).isPresent());
        assertTrue(targetRegistry.find(JdbcReplicaCdcTargetConnector.TARGET_ID).isPresent());

        CdcTargetConnector kafkaTargetConnector = targetRegistry.find(KafkaCdcTargetConnector.TARGET_ID).orElseThrow();
        assertFalse(kafkaTargetConnector.scaffoldOnly());
        kafkaTargetConnector.validate(Map.of());
        assertThrows(UnsupportedOperationException.class, () -> kafkaTargetConnector.write(List.of()));
    }
}
