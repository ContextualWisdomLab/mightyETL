package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specifies fail-closed activation and bounded durable-job worker configuration.
 */
class EtlJobWorkerPropertiesTest {

    private static final long MAXIMUM_SCHEDULER_DELAY_MILLISECONDS = 86_400_000L;
    private static final long MAXIMUM_LEASE_DURATION_SECONDS = 86_400L;

    @Test
    void defaultsToDisabledBoundedPollingWithGeneratedSafeOwner() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        assertFalse(properties.isEnabled());
        assertEquals(5_000L, properties.getFixedDelayMilliseconds());
        assertEquals(5_000L, properties.getInitialDelayMilliseconds());
        assertEquals(300L, properties.getLeaseDurationSeconds());
        assertEquals(3, properties.getMaxAttempts());
        assertTrue(properties.getLeaseOwnerId().matches("[A-Za-z0-9._:-]{8,128}"));

        EtlJobWorkerProperties another = new EtlJobWorkerProperties();
        assertNotEquals(properties.getLeaseOwnerId(), another.getLeaseOwnerId());
    }

    @Test
    void acceptsEverySupportedBoundary() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        properties.setEnabled(true);
        properties.setFixedDelayMilliseconds(1L);
        properties.setInitialDelayMilliseconds(0L);
        properties.setLeaseDurationSeconds(1L);
        properties.setMaxAttempts(1);
        properties.setLeaseOwnerId("worker-01");

        assertTrue(properties.isEnabled());
        assertEquals(1L, properties.getFixedDelayMilliseconds());
        assertEquals(0L, properties.getInitialDelayMilliseconds());
        assertEquals(1L, properties.getLeaseDurationSeconds());
        assertEquals(1, properties.getMaxAttempts());
        assertEquals("worker-01", properties.getLeaseOwnerId());

        properties.setFixedDelayMilliseconds(MAXIMUM_SCHEDULER_DELAY_MILLISECONDS);
        properties.setInitialDelayMilliseconds(MAXIMUM_SCHEDULER_DELAY_MILLISECONDS);
        properties.setLeaseDurationSeconds(MAXIMUM_LEASE_DURATION_SECONDS);
        properties.setMaxAttempts(100);
        properties.setLeaseOwnerId("w".repeat(128));
        assertEquals(MAXIMUM_SCHEDULER_DELAY_MILLISECONDS, properties.getFixedDelayMilliseconds());
        assertEquals(MAXIMUM_SCHEDULER_DELAY_MILLISECONDS, properties.getInitialDelayMilliseconds());
        assertEquals(MAXIMUM_LEASE_DURATION_SECONDS, properties.getLeaseDurationSeconds());
        assertEquals(100, properties.getMaxAttempts());
        assertEquals(128, properties.getLeaseOwnerId().length());
    }

    @Test
    void rejectsUnsafeNumericConfiguration() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setFixedDelayMilliseconds(0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setFixedDelayMilliseconds(
                        MAXIMUM_SCHEDULER_DELAY_MILLISECONDS + 1L
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setInitialDelayMilliseconds(-1L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setInitialDelayMilliseconds(
                        MAXIMUM_SCHEDULER_DELAY_MILLISECONDS + 1L
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseDurationSeconds(0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseDurationSeconds(MAXIMUM_LEASE_DURATION_SECONDS + 1L)
        );
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttempts(101));
    }

    @Test
    void rejectsMissingShortLongOrUnsafeOwnerIdentifiers() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        assertThrows(NullPointerException.class, () -> properties.setLeaseOwnerId(null));
        assertThrows(IllegalArgumentException.class, () -> properties.setLeaseOwnerId("short"));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseOwnerId("w".repeat(129))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseOwnerId("worker identifier")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseOwnerId("worker/identifier")
        );
    }
}
