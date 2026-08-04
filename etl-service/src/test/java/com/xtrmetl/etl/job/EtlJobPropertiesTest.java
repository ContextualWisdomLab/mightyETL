package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies safe defaults and bounded overrides for the durable ETL job worker.
 */
class EtlJobPropertiesTest {

    @Test
    void usesStandaloneProductDefaults() {
        EtlJobProperties properties = new EtlJobProperties();

        assertTrue(properties.isEnabled());
        assertEquals(1_000, properties.getPollDelayMs());
        assertEquals(300, properties.getLeaseDurationSeconds());
        assertEquals(5, properties.getMaxAttempts());
    }

    @Test
    void acceptsSupportedOverrides() {
        EtlJobProperties properties = new EtlJobProperties();

        properties.setEnabled(false);
        properties.setPollDelayMs(100);
        properties.setLeaseDurationSeconds(30);
        properties.setMaxAttempts(100);

        assertFalse(properties.isEnabled());
        assertEquals(100, properties.getPollDelayMs());
        assertEquals(30, properties.getLeaseDurationSeconds());
        assertEquals(100, properties.getMaxAttempts());
    }

    @Test
    void rejectsUnsafeWorkerSettings() {
        EtlJobProperties properties = new EtlJobProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setPollDelayMs(99));
        assertThrows(IllegalArgumentException.class, () -> properties.setPollDelayMs(60_001));
        assertThrows(IllegalArgumentException.class, () -> properties.setLeaseDurationSeconds(29));
        assertThrows(IllegalArgumentException.class, () -> properties.setLeaseDurationSeconds(86_401));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttempts(101));
    }
}
