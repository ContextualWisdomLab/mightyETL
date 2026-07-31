package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.connector.ConnectorProperties;
import com.xtrmetl.etl.connector.TargetConnectorDispatcher;
import com.xtrmetl.etl.connector.TargetConnectorRegistry;
import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtlControllerTest {

    private EtlService etlService;
    private EtlController etlController;

    @BeforeEach
    void setUp() {
        etlService = mock(EtlService.class);
        TargetConnectorDispatcher dispatcher =
                new TargetConnectorDispatcher(new TargetConnectorRegistry(), new ConnectorProperties());
        etlController = new EtlController(etlService, dispatcher);
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

    @Test
    void connectorsCatalogListsScaffoldTargets() {
        ResponseEntity<Map<String, Object>> response = etlController.connectors();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals("mightyETL", body.get("product"));
        assertEquals("postgresql", body.get("primaryLoadPath"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> connectors = (List<Map<String, Object>>) body.get("connectors");
        assertEquals(3, connectors.size());
        assertTrue(connectors.stream().anyMatch(c -> "databricks".equals(c.get("id"))));
    }
}
