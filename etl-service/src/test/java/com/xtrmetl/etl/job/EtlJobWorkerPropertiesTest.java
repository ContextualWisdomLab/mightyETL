package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines fail-closed defaults and hard resource bounds for durable ETL workers.
 */
class EtlJobWorkerPropertiesTest {

    @Test
    void usesFailClosedBoundedDefaults() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        assertFalse(properties.isEnabled());
        assertEquals(5_000L, properties.getPollDelayMillis());
        assertEquals(300_000L, properties.getLeaseDurationMillis());
        assertEquals(5_000L, properties.getRetryDelayMillis());
        assertEquals(3, properties.getMaxAttempts());
        assertEquals(1, properties.getJobsPerPoll());
    }

    @Test
    void acceptsEveryDocumentedBoundary() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        properties.setEnabled(true);
        properties.setPollDelayMillis(100L);
        properties.setLeaseDurationMillis(1_000L);
        properties.setRetryDelayMillis(1L);
        properties.setMaxAttempts(1);
        properties.setJobsPerPoll(1);

        assertTrue(properties.isEnabled());
        assertEquals(100L, properties.getPollDelayMillis());
        assertEquals(1_000L, properties.getLeaseDurationMillis());
        assertEquals(1L, properties.getRetryDelayMillis());
        assertEquals(1, properties.getMaxAttempts());
        assertEquals(1, properties.getJobsPerPoll());

        properties.setPollDelayMillis(3_600_000L);
        properties.setLeaseDurationMillis(86_400_000L);
        properties.setRetryDelayMillis(3_600_000L);
        properties.setMaxAttempts(20);
        properties.setJobsPerPoll(32);

        assertEquals(3_600_000L, properties.getPollDelayMillis());
        assertEquals(86_400_000L, properties.getLeaseDurationMillis());
        assertEquals(3_600_000L, properties.getRetryDelayMillis());
        assertEquals(20, properties.getMaxAttempts());
        assertEquals(32, properties.getJobsPerPoll());
    }

    @Test
    void rejectsEveryOutOfRangeSetting() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setPollDelayMillis(99L));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setPollDelayMillis(3_600_001L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseDurationMillis(999L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setLeaseDurationMillis(86_400_001L)
        );
        assertThrows(IllegalArgumentException.class, () -> properties.setRetryDelayMillis(0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setRetryDelayMillis(3_600_001L)
        );
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAttempts(21));
        assertThrows(IllegalArgumentException.class, () -> properties.setJobsPerPoll(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setJobsPerPoll(33));
    }

    @Test
    void calculatesCappedAttemptScaledRetryDelay() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();
        properties.setRetryDelayMillis(2_000L);

        assertEquals(2_000L, properties.retryDelayForAttempt(1));
        assertEquals(6_000L, properties.retryDelayForAttempt(3));

        properties.setRetryDelayMillis(3_600_000L);
        assertEquals(3_600_000L, properties.retryDelayForAttempt(20));
        assertThrows(IllegalArgumentException.class, () -> properties.retryDelayForAttempt(0));
    }
}
