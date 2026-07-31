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
        CdcTargetRegistry registry = new CdcTargetRegistry();

        assertEquals(2, registry.all().size());
        assertTrue(registry.find(KafkaCdcTargetConnector.ID).isPresent());
        assertTrue(registry.find(JdbcReplicaCdcTargetConnector.ID).isPresent());

        CdcTargetConnector kafka = registry.find(KafkaCdcTargetConnector.ID).orElseThrow();
        assertFalse(kafka.scaffoldOnly());
        kafka.validate(Map.of());
        assertThrows(UnsupportedOperationException.class, () -> kafka.write(List.of()));
    }
}
