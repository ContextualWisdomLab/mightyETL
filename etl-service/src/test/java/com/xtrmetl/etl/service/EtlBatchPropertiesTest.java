package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies default, supported, and invalid values for ETL batch safety settings.
 */
class EtlBatchPropertiesTest {

    @Test
    void usesConservativeDefaults() {
        EtlBatchProperties properties = new EtlBatchProperties();

        assertEquals(EtlBatchProperties.DEFAULT_MAX_PAYLOAD_BYTES, properties.getMaxPayloadBytes());
        assertEquals(EtlBatchProperties.DEFAULT_MAX_BATCH_RECORDS, properties.getMaxBatchRecords());
    }

    @Test
    void acceptsPositiveOverridesAtSupportedCeilings() {
        EtlBatchProperties properties = new EtlBatchProperties();

        properties.setMaxPayloadBytes(EtlBatchProperties.MAX_MAX_PAYLOAD_BYTES);
        properties.setMaxBatchRecords(EtlBatchProperties.MAX_MAX_BATCH_RECORDS);

        assertEquals(EtlBatchProperties.MAX_MAX_PAYLOAD_BYTES, properties.getMaxPayloadBytes());
        assertEquals(EtlBatchProperties.MAX_MAX_BATCH_RECORDS, properties.getMaxBatchRecords());
    }

    @Test
    void rejectsPayloadLimitsOutsideSupportedRange() {
        EtlBatchProperties properties = new EtlBatchProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxPayloadBytes(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxPayloadBytes(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setMaxPayloadBytes(EtlBatchProperties.MAX_MAX_PAYLOAD_BYTES + 1)
        );
    }

    @Test
    void rejectsRecordLimitsOutsideSupportedRange() {
        EtlBatchProperties properties = new EtlBatchProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxBatchRecords(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxBatchRecords(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setMaxBatchRecords(EtlBatchProperties.MAX_MAX_BATCH_RECORDS + 1)
        );
    }
}
