package com.xtrmetl.cdc.controller;

import com.xtrmetl.cdc.config.XtrmetlProperties;
import com.xtrmetl.cdc.service.CdcService;
import com.xtrmetl.cdc.service.ReplicationSlotProbe;
import com.xtrmetl.cdc.spi.CdcSourceFactory;
import com.xtrmetl.cdc.spi.CdcSourceRegistry;
import com.xtrmetl.cdc.spi.CdcTargetRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CdcControllerTest {

    private CdcService cdcService;
    private XtrmetlProperties xtrmetlProperties;
    private CdcSourceRegistry sourceRegistry;
    private CdcTargetRegistry targetRegistry;
    private CdcSourceFactory sourceFactory;
    private ReplicationSlotProbe replicationSlotProbe;
    private CdcController cdcController;

    @BeforeEach
    void setUp() {
        cdcService = mock(CdcService.class);
        xtrmetlProperties = new XtrmetlProperties();
        sourceRegistry = new CdcSourceRegistry();
        targetRegistry = new CdcTargetRegistry();
        sourceFactory = new CdcSourceFactory(sourceRegistry);
        replicationSlotProbe = mock(ReplicationSlotProbe.class);
        when(replicationSlotProbe.probeConfiguredSlot()).thenReturn(Map.of(
                "slotName", "xtrmetl_slot",
                "available", true,
                "found", true,
                "active", true,
                "restartLagBytes", 0L
        ));
        cdcController = new CdcController(
                cdcService,
                xtrmetlProperties,
                sourceRegistry,
                targetRegistry,
                sourceFactory,
                replicationSlotProbe
        );
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

    @Test
    void testStatusIncludesSlotSourcesAndTargets() {
        Map<String, Object> serviceStatus = new LinkedHashMap<>();
        serviceStatus.put("running", false);
        serviceStatus.put("product", "mightyETL");
        when(cdcService.getStatus()).thenReturn(serviceStatus);

        ResponseEntity<Map<String, Object>> response = cdcController.cdcStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> statusBody = response.getBody();
        assertEquals(false, statusBody.get("running"));
        assertEquals("mightyETL", statusBody.get("product"));
        assertEquals(false, statusBody.get("replicaEnabled"));
        assertTrue(statusBody.containsKey("registeredSources"));
        assertTrue(statusBody.containsKey("registeredTargets"));
        assertTrue(statusBody.containsKey("configuredSources"));
        assertTrue(statusBody.containsKey("replicationSlot"));
        @SuppressWarnings("unchecked")
        Map<String, Object> replicationSlot =
                (Map<String, Object>) statusBody.get("replicationSlot");
        assertEquals(true, replicationSlot.get("found"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourceEntries =
                (List<Map<String, Object>>) statusBody.get("registeredSources");
        assertFalse(sourceEntries.isEmpty());
        assertEquals("postgres-debezium", sourceEntries.get(0).get("id"));
    }

    @Test
    void testSourcesListsPostgresDebezium() {
        ResponseEntity<List<Map<String, Object>>> response = cdcController.cdcSources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> sourceEntries = response.getBody();
        assertFalse(sourceEntries.isEmpty());
        assertTrue(sourceEntries.stream().anyMatch(
                sourceEntry -> "postgres-debezium".equals(sourceEntry.get("id"))
        ));
    }

    @Test
    void testTargetsListsKafkaAndJdbcReplica() {
        ResponseEntity<List<Map<String, Object>>> response = cdcController.cdcTargets();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> targetEntries = response.getBody();
        assertEquals(2, targetEntries.size());
        assertTrue(targetEntries.stream().anyMatch(
                targetEntry -> "kafka".equals(targetEntry.get("id"))
        ));
        assertTrue(targetEntries.stream().anyMatch(
                targetEntry -> "jdbc-replica".equals(targetEntry.get("id"))
        ));
    }
}
