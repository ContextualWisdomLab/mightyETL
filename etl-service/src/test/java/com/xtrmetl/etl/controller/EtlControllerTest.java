package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EtlControllerTest {

    @Mock
    private EtlService etlService;

    @InjectMocks
    private EtlController etlController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessData() {
        String testData = "[{\"id\":\"1\",\"name\":\"John Doe\"}]";
        when(etlService.processData(testData)).thenReturn("Processed: 1");

        ResponseEntity<String> response = etlController.processData(testData);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Processed: 1", response.getBody());
        verify(etlService, times(1)).processData(testData);
    }

    @Test
    void testProcessDataWithException() {
        String testData = "invalid data";
        when(etlService.processData(testData)).thenThrow(new RuntimeException("Error processing data"));

        ResponseEntity<String> response = etlController.processData(testData);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error processing data: Error processing data", response.getBody());
        verify(etlService, times(1)).processData(testData);
    }
}
