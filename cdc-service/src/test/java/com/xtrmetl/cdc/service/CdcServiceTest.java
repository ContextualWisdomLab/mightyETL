package com.xtrmetl.cdc.service;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CdcServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;

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
        RecordChangeEvent<SourceRecord> changeEvent = mock(RecordChangeEvent.class);
        SourceRecord sourceRecord = mock(SourceRecord.class);

        when(changeEvent.record()).thenReturn(sourceRecord);
        when(sourceRecord.topic()).thenReturn("test-topic");
        when(sourceRecord.key()).thenReturn("test-key");
        when(sourceRecord.value()).thenReturn("test-value");

        cdcService.handleChangeEvent(changeEvent);

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }
}
