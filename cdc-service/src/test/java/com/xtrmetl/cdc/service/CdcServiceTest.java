package com.xtrmetl.cdc.service;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CdcServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;

    @InjectMocks
    private CdcService cdcService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(cdcService, "debeziumEngine", debeziumEngine);
    }

    @Test
    void testStart() {
        assertDoesNotThrow(() -> cdcService.start());
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
        assertEquals("Error stopping CDC: Test exception", exception.getMessage());

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
