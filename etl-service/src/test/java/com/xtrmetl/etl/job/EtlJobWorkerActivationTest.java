package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Keeps worker scheduling fail-closed and fixed-delay based.
 */
class EtlJobWorkerActivationTest {

    @Test
    void workerRequiresExplicitActivation() {
        ConditionalOnBooleanProperty condition = EtlJobWorker.class.getAnnotation(
                ConditionalOnBooleanProperty.class
        );

        assertNotNull(condition);
        assertEquals("xtrmetl.etl.jobs.worker", condition.prefix());
        assertEquals("enabled", condition.name());
        assertFalse(condition.matchIfMissing());
    }

    @Test
    void pollUsesCompletionBasedFixedDelay() throws NoSuchMethodException {
        Method poll = EtlJobWorker.class.getMethod("poll");
        Scheduled scheduled = poll.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals(
                "${xtrmetl.etl.jobs.worker.poll-delay-millis:5000}",
                scheduled.fixedDelayString()
        );
    }

    @Test
    void schedulingInfrastructureIsExplicitlyEnabled() {
        assertNotNull(
                EtlJobSchedulingConfiguration.class.getAnnotation(EnableScheduling.class)
        );
    }
}
