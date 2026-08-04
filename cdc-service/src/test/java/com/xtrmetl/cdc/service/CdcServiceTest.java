package com.xtrmetl.cdc.service;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CdcServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;

    private CdcService cdcService;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        kafkaTemplate = template;
        @SuppressWarnings("unchecked")
        DebeziumEngine<ChangeEvent<String, String>> engine =
                (DebeziumEngine<ChangeEvent<String, String>>) mock(DebeziumEngine.class);
        debeziumEngine = engine;

        cdcService = new CdcService(kafkaTemplate, false);
        ReflectionTestUtils.setField(cdcService, "debeziumEngine", debeziumEngine);
    }

    @Test
    void testStart() {
        assertDoesNotThrow(() -> cdcService.start());
    }

    @Test
    void getStatusReportsNotRunningAndMightyEtlProduct() {
        Map<String, Object> status = cdcService.getStatus();

        assertEquals("mightyETL", status.get("product"));
        assertEquals(false, status.get("running"));
        assertEquals(false, status.get("autoStart"));
        assertEquals("postgres-debezium", status.get("sourceType"));
        assertEquals(false, status.get("anyToAny"));
        assertTrue(status.containsKey("topicPrefix"));
        assertTrue(status.containsKey("tableIncludeList"));
    }

    @Test
    void isRunningReflectsEngineTask() throws InterruptedException {
        CountDownLatch engineStarted = new CountDownLatch(1);
        CountDownLatch allowEngineExit = new CountDownLatch(1);
        doAnswer(invocation -> {
            engineStarted.countDown();
            allowEngineExit.await();
            return null;
        }).when(debeziumEngine).run();

        assertFalse(cdcService.isRunning());
        try {
            cdcService.start();
            assertTrue(engineStarted.await(5, TimeUnit.SECONDS));
            assertTrue(cdcService.isRunning());
        } finally {
            allowEngineExit.countDown();
            cdcService.shutdown();
        }
        assertFalse(cdcService.isRunning());
    }

    @Test
    void maybeAutoStartDoesNothingWhenDisabled() {
        CdcService service = new CdcService(kafkaTemplate, false);
        ReflectionTestUtils.setField(service, "debeziumEngine", debeziumEngine);

        service.maybeAutoStart();

        assertNull(ReflectionTestUtils.getField(service, "engineTask"));
    }

    @Test
    void maybeAutoStartStartsEngineWhenEnabled() {
        CdcService service = new CdcService(kafkaTemplate, true);
        ReflectionTestUtils.setField(service, "debeziumEngine", debeziumEngine);

        service.maybeAutoStart();

        assertNotNull(ReflectionTestUtils.getField(service, "engineTask"));
        service.shutdown();
    }

    @Test
    void shutdownStopsEngineAndClearsExecutor() throws IOException {
        cdcService.start();

        cdcService.shutdown();

        assertNull(ReflectionTestUtils.getField(cdcService, "executor"));
        assertNull(ReflectionTestUtils.getField(cdcService, "engineTask"));
        verify(debeziumEngine, times(1)).close();
    }

    @Test
    void shutdownInterruptsThreadAndForcesShutdownNow() throws InterruptedException {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("test"));

        CdcService service = new CdcService(kafkaTemplate, false);
        ReflectionTestUtils.setField(service, "executor", executor);

        service.shutdown();

        verify(executor, times(1)).shutdown();
        verify(executor, times(1)).shutdownNow();
        try {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear interrupt status to avoid leaking it into subsequent tests.
            Thread.interrupted();
        }
    }

    @Test
    void testStop() throws IOException {
        // Call the stop method
        cdcService.stop();

        // Verify that close() was called on the debeziumEngine
        verify(debeziumEngine, times(1)).close();

        // Verify that debeziumEngine is set to null after stopping
        assertNull(ReflectionTestUtils.getField(cdcService, "debeziumEngine"));
    }

    @Test
    void testStopWithException() throws IOException {
        // Mock the debeziumEngine to throw an IOException when close() is called
        doThrow(new IOException("Test exception")).when(debeziumEngine).close();

        // Call the stop method and expect an IOException
        IOException exception = assertThrows(IOException.class, () -> cdcService.stop());

        // Verify the exception message
        assertEquals("Error stopping CDC", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("Test exception", exception.getCause().getMessage());

        // Verify that close() was called on the debeziumEngine
        verify(debeziumEngine, times(1)).close();

        // Verify that debeziumEngine is set to null even when an exception occurs
        assertNull(ReflectionTestUtils.getField(cdcService, "debeziumEngine"));
    }

    @Test
    void testHandleChangeEvent() {
        @SuppressWarnings("unchecked")
        ChangeEvent<String, String> changeEvent =
            (ChangeEvent<String, String>) mock(ChangeEvent.class);

        when(changeEvent.destination()).thenReturn("test-topic");
        when(changeEvent.key()).thenReturn("test-key");
        when(changeEvent.value()).thenReturn("test-value");

        cdcService.handleChangeEvent(changeEvent);

        verify(kafkaTemplate, times(1)).send(eq("test-topic"), eq("test-key"), eq("test-value"));
    }

    @Test
    void testHandleChangeEventWithoutKey() {
        @SuppressWarnings("unchecked")
        ChangeEvent<String, String> changeEvent =
            (ChangeEvent<String, String>) mock(ChangeEvent.class);

        when(changeEvent.destination()).thenReturn("test-topic");
        when(changeEvent.key()).thenReturn(null);
        when(changeEvent.value()).thenReturn("test-value");

        cdcService.handleChangeEvent(changeEvent);

        verify(kafkaTemplate, times(1)).send(eq("test-topic"), eq("test-value"));
        verify(kafkaTemplate, never()).send(eq("test-topic"), anyString(), eq("test-value"));
    }
}
