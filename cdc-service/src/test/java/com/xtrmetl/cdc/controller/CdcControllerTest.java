package com.xtrmetl.cdc.controller;

import com.xtrmetl.cdc.service.CdcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CdcControllerTest {

    @Mock
    private CdcService cdcService;

    @InjectMocks
    private CdcController cdcController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testStartCdc() {
        ResponseEntity<String> response = cdcController.startCdc();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CDC process started", response.getBody());
        verify(cdcService, times(1)).start();
    }

    @Test
    void testStopCdc() throws IOException {
        ResponseEntity<String> response = cdcController.stopCdc();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CDC process stopped", response.getBody());
        verify(cdcService, times(1)).stop();
    }

    @Test
    void testStopCdcWithException() throws IOException {
        doThrow(new IOException("Error stopping CDC")).when(cdcService).stop();

        ResponseEntity<String> response = cdcController.stopCdc();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Error stopping CDC process: Error stopping CDC", response.getBody());
        verify(cdcService, times(1)).stop();
    }
}
