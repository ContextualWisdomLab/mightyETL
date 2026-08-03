package com.xtrmetl.etl.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies ETL worker bounds, naming, validation, and caller-runs backpressure.
 */
class EtlExecutorConfigurationTest {

    @Test
    void propertiesExposeSafeDefaults() {
        EtlProcessingProperties properties = new EtlProcessingProperties();

        assertEquals(1000, properties.getMaxBatchRecords());
        assertTrue(properties.getMaxConcurrency() >= 1);
        assertTrue(properties.getMaxConcurrency() <= 8);
        assertEquals(1024, properties.getQueueCapacity());
    }

    @Test
    void createsFixedNamedExecutorWithBoundedQueue() throws Exception {
        EtlProcessingProperties properties = properties(2, 7, 100);
        ExecutorService executor = new EtlExecutorConfiguration().etlExecutor(properties);

        try {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
            assertEquals(2, pool.getCorePoolSize());
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(7, pool.getQueue().remainingCapacity());
            assertTrue(executor.submit(() -> Thread.currentThread().getName())
                    .get(5, TimeUnit.SECONDS)
                    .startsWith("mighty-etl-worker-"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void usesCallerThreadWhenWorkerAndQueueAreSaturated() throws Exception {
        EtlProcessingProperties properties = properties(1, 1, 100);
        ExecutorService executor = new EtlExecutorConfiguration().etlExecutor(properties);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<String> backpressureThread = new AtomicReference<>();
        String callerThread = Thread.currentThread().getName();

        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS));
            executor.execute(() -> await(releaseWorker));

            executor.execute(() -> backpressureThread.set(Thread.currentThread().getName()));

            assertEquals(callerThread, backpressureThread.get());
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNonPositiveBatchLimit() {
        EtlProcessingProperties properties = properties(1, 1, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new EtlExecutorConfiguration().etlExecutor(properties));
    }

    @Test
    void rejectsNonPositiveConcurrency() {
        EtlProcessingProperties properties = properties(0, 1, 100);

        assertThrows(IllegalArgumentException.class,
                () -> new EtlExecutorConfiguration().etlExecutor(properties));
    }

    @Test
    void rejectsNonPositiveQueueCapacity() {
        EtlProcessingProperties properties = properties(1, 0, 100);

        assertThrows(IllegalArgumentException.class,
                () -> new EtlExecutorConfiguration().etlExecutor(properties));
    }

    private static EtlProcessingProperties properties(
            int concurrency,
            int queueCapacity,
            int maxBatchRecords
    ) {
        EtlProcessingProperties properties = new EtlProcessingProperties();
        properties.setMaxConcurrency(concurrency);
        properties.setQueueCapacity(queueCapacity);
        properties.setMaxBatchRecords(maxBatchRecords);
        return properties;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test latch interrupted", exception);
        }
    }
}
