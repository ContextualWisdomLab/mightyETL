package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.config.EtlProcessingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies admission control before writes and explicit executor use for ETL batches.
 */
class EtlServiceBatchSafetyTest {

    @Test
    void rejectsNullConstructionDependencies() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();

        assertThrows(NullPointerException.class,
                () -> new EtlService(null, objectMapper));
        assertThrows(NullPointerException.class,
                () -> new EtlService(jdbcTemplate, null));
    }

    @Test
    void rejectsNullExecutionPolicyDependencies() {
        EtlService service = new EtlService(mock(JdbcTemplate.class), new ObjectMapper());
        EtlProcessingProperties properties = new EtlProcessingProperties();

        assertThrows(NullPointerException.class,
                () -> service.configureExecution(null, properties));
        assertThrows(NullPointerException.class,
                () -> service.configureExecution(Runnable::run, null));
    }

    @Test
    void rejectsOversizedBatchBeforeSchedulingOrWriting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecordingExecutor executor = new RecordingExecutor();
        EtlService service = service(jdbcTemplate, executor, 1);

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.processData("[{\"id\":\"1\"},{\"id\":\"2\"}]")
        );

        assertTrue(error.getMessage().contains("maximum is 1"));
        assertEquals(0, executor.executions.get());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void acceptsBatchAtConfiguredMaximum() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecordingExecutor executor = new RecordingExecutor();
        EtlService service = service(jdbcTemplate, executor, 2);

        String result = service.processData("[{\"id\":1},{\"id\":2}]");

        assertEquals("Processed: 1\nProcessed: 2", result);
        assertEquals(2, executor.executions.get());
        verify(jdbcTemplate, times(2)).update(anyString(), anyString());
    }

    @Test
    void validatesTheWholeBatchBeforeTheFirstDatabaseWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecordingExecutor executor = new RecordingExecutor();
        EtlService service = service(jdbcTemplate, executor, 10);

        assertThrows(
                RuntimeException.class,
                () -> service.processData("[{\"id\":\"valid\"},{\"name\":\"missing id\"}]")
        );

        assertEquals(0, executor.executions.get());
        verifyNoInteractions(jdbcTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"id\":null}]",
            "[{\"id\":\"   \"}]",
            "[{\"id\":true}]",
            "[{\"id\":1.5}]",
            "[{\"id\":{\"nested\":\"value\"}}]",
            "[[]]",
            "[null]"
    })
    void rejectsInvalidRecordIdentifiersBeforeWriting(String input) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlService service = service(jdbcTemplate, Runnable::run, 10);

        assertThrows(RuntimeException.class, () -> service.processData(input));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void submitsEveryAcceptedRecordToTheConfiguredExecutor() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecordingExecutor executor = new RecordingExecutor();
        EtlService service = service(jdbcTemplate, executor, 10);

        String result = service.processData("[{\"id\":\"1\"},{\"id\":\"2\"}]");

        assertEquals(2, executor.executions.get());
        assertEquals("Processed: 1\nProcessed: 2", result);
        verify(jdbcTemplate, times(2)).update(anyString(), anyString());
    }

    @Test
    void preservesInputResultOrderWhenTasksCompleteInReverse() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ManualExecutor executor = new ManualExecutor(2);
        EtlService service = service(jdbcTemplate, executor, 10);
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        CompletableFuture<String> request = null;

        try {
            request = CompletableFuture.supplyAsync(
                    () -> service.processData("[{\"id\":\"first\"},{\"id\":\"second\"}]"),
                    requestExecutor
            );
            assertTrue(executor.allTasksSubmitted.await(5, TimeUnit.SECONDS));

            executor.runInReverseOrder();

            assertEquals(
                    "Processed: first\nProcessed: second",
                    request.get(5, TimeUnit.SECONDS)
            );
        } finally {
            executor.runInReverseOrder();
            if (request != null) {
                request.cancel(true);
            }
            requestExecutor.shutdownNow();
        }
    }

    private static EtlService service(
            JdbcTemplate jdbcTemplate,
            Executor executor,
            int maxBatchRecords
    ) {
        EtlProcessingProperties properties = new EtlProcessingProperties();
        properties.setMaxBatchRecords(maxBatchRecords);
        EtlService service = new EtlService(jdbcTemplate, new ObjectMapper());
        service.configureExecution(executor, properties);
        return service;
    }

    private static final class RecordingExecutor implements Executor {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            executions.incrementAndGet();
            command.run();
        }
    }

    private static final class ManualExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();
        private final CountDownLatch allTasksSubmitted;

        private ManualExecutor(int expectedTasks) {
            this.allTasksSubmitted = new CountDownLatch(expectedTasks);
        }

        @Override
        public synchronized void execute(Runnable command) {
            tasks.add(command);
            allTasksSubmitted.countDown();
        }

        private void runInReverseOrder() {
            List<Runnable> snapshot;
            synchronized (this) {
                snapshot = List.copyOf(tasks);
            }
            for (int index = snapshot.size() - 1; index >= 0; index--) {
                snapshot.get(index).run();
            }
        }
    }
}
