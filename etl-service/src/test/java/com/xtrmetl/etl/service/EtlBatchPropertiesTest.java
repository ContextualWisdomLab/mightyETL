package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies default and invalid values for ETL batch safety settings.
 */
class EtlBatchPropertiesTest {

    @Test
    void usesConservativeDefaults() {
        EtlBatchProperties properties = new EtlBatchProperties();

        assertEquals(EtlBatchProperties.DEFAULT_MAX_PAYLOAD_BYTES, properties.getMaxPayloadBytes());
        assertEquals(EtlBatchProperties.DEFAULT_MAX_BATCH_RECORDS, properties.getMaxBatchRecords());
    }

    @Test
    void acceptsPositiveOverrides() {
        EtlBatchProperties properties = new EtlBatchProperties();

        properties.setMaxPayloadBytes(2_048);
        properties.setMaxBatchRecords(25);

        assertEquals(2_048, properties.getMaxPayloadBytes());
        assertEquals(25, properties.getMaxBatchRecords());
    }

    @Test
    void rejectsNonPositivePayloadLimit() {
        EtlBatchProperties properties = new EtlBatchProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxPayloadBytes(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxPayloadBytes(-1));
    }

    @Test
    void rejectsNonPositiveRecordLimit() {
        EtlBatchProperties properties = new EtlBatchProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxBatchRecords(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxBatchRecords(-1));
    }
}
