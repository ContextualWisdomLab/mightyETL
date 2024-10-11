package com.xtrmetl.cdc.service;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CdcServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private DebeziumEngine<ChangeEvent<SourceRecord>> debeziumEngine;

    @InjectMocks
    private CdcService cdcService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testStart() {
        assertDoesNotThrow(() -> cdcService.start());
    }

    @Test
    void testStop() throws IOException {
        assertDoesNotThrow(() -> cdcService.stop());
        verify(debeziumEngine, times(1)).close();
    }

    @Test
    void testHandleChangeEvent() {
        ChangeEvent<SourceRecord> changeEvent = mock(ChangeEvent.class);
        SourceRecord sourceRecord = mock(SourceRecord.class);

        when(changeEvent.record()).thenReturn(sourceRecord);
        when(sourceRecord.topic()).thenReturn("test-topic");
        when(sourceRecord.key()).thenReturn("test-key");
        when(sourceRecord.value()).thenReturn("test-value");

        cdcService.handleChangeEvent(changeEvent);

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }
}
