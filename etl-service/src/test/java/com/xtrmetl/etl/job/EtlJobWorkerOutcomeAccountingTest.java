package com.xtrmetl.etl.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies that one completed worker poll contributes exactly one terminal outcome observation.
 *
 * <p>The outcome counter and duration timer are intended to share one finite terminal vocabulary.
 * A successful poll therefore records only {@code succeeded}; an intermediate claim event must not
 * be counted as a second outcome because summing the outcome series is an operator-facing poll-rate
 * and service-level evidence boundary.</p>
 */
@ExtendWith(MockitoExtension.class)
class EtlJobWorkerOutcomeAccountingTest {

    private static final String OWNER_ID = "worker-alpha";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);

    @Mock
    private EtlJobLeaseRepository leaseRepository;

    @Mock
    private EtlJobExecutionService executionService;

    @Test
    void successfulPollRecordsExactlyOneTerminalCounterAndDuration() {
        EtlJobWorkerProperties properties = new EtlJobWorkerProperties();
        properties.setEnabled(true);
        properties.setLeaseOwnerId(OWNER_ID);
        properties.setLeaseDurationSeconds(120L);
        properties.setMaxAttempts(3);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EtlJobLease lease = new EtlJobLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OWNER_ID,
                HASH_A,
                HASH_B,
                HASH_C,
                "[{\"id\":\"record_alpha\"}]",
                1,
                Instant.now().plusSeconds(300)
        );
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));

        new EtlJobWorker(
                leaseRepository,
                executionService,
                properties,
                meterRegistry
        ).pollOnce();

        double totalOutcomeCount = meterRegistry.find("etl.jobs.worker.outcomes")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        long totalDurationCount = meterRegistry.find("etl.jobs.execution.duration")
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();

        assertEquals(1.0, totalOutcomeCount);
        assertEquals(1L, totalDurationCount);
        assertEquals(
                1.0,
                meterRegistry.find("etl.jobs.worker.outcomes")
                        .tag("outcome", "succeeded")
                        .counter()
                        .count()
        );
        assertNull(
                meterRegistry.find("etl.jobs.worker.outcomes")
                        .tag("outcome", "claimed")
                        .counter()
        );
    }
}
