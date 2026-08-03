package com.xtrmetl.etl.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates the dedicated bounded executor used by ETL record transformation tasks.
 */
@Configuration
public class EtlExecutorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EtlExecutorConfiguration.class);

    /**
     * Builds a fixed-size executor with a bounded queue and caller-runs backpressure.
     *
     * <p>When the worker set and queue are saturated, the submitting request thread performs
     * the transformation. This slows producers without losing tasks or consuming an unbounded
     * amount of heap. Submission after shutdown fails explicitly.</p>
     *
     * @param properties validated ETL resource limits
     * @return managed ETL executor service
     */
    @Bean(name = "etlExecutor", destroyMethod = "shutdown")
    public ExecutorService etlExecutor(EtlProcessingProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        properties.validate();

        AtomicInteger workerSequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(
                    task,
                    "mighty-etl-worker-" + workerSequence.incrementAndGet()
            );
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler((failedThread, exception) ->
                    log.error("Uncaught ETL worker failure on {}", failedThread.getName(), exception));
            return thread;
        };

        return new ThreadPoolExecutor(
                properties.getMaxConcurrency(),
                properties.getMaxConcurrency(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                threadFactory,
                (task, executor) -> {
                    if (executor.isShutdown()) {
                        throw new RejectedExecutionException("ETL executor is shut down");
                    }
                    task.run();
                }
        );
    }
}
