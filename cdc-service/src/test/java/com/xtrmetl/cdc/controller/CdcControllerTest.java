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
    private XtrmetlProperties properties;
    private CdcSourceRegistry sourceRegistry;
    private CdcTargetRegistry targetRegistry;
    private CdcSourceFactory sourceFactory;
    private ReplicationSlotProbe replicationSlotProbe;
    private CdcController cdcController;

    @BeforeEach
    void setUp() {
        cdcService = mock(CdcService.class);
        properties = new XtrmetlProperties();
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
                properties,
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
    void testStopCdcWithExceptionDoesNotExposeInternalDiagnostics() throws IOException {
        String sensitiveDiagnostic =
                "Error closing offset store at jdbc:postgresql://db.internal/prod?password=driver-secret";
        doThrow(new IOException(sensitiveDiagnostic)).when(cdcService).stop();

        ResponseEntity<String> response = cdcController.stopCdc();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("CDC process could not be stopped", response.getBody());
        assertFalse(response.getBody().contains("driver-secret"));
        assertFalse(response.getBody().contains("jdbc:postgresql://"));
        verify(cdcService, times(1)).stop();
    }

    @Test
    void testStatusIncludesSlotSourcesAndTargets() {
        Map<String, Object> serviceStatus = new LinkedHashMap<>();
        serviceStatus.put("running", false);
        serviceStatus.put("product", "mightyETL");
        when(cdcService.getStatus()).thenReturn(serviceStatus);

        ResponseEntity<Map<String, Object>> response = cdcController.status();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(false, body.get("running"));
        assertEquals("mightyETL", body.get("product"));
        assertEquals(false, body.get("replicaEnabled"));
        assertTrue(body.containsKey("registeredSources"));
        assertTrue(body.containsKey("registeredTargets"));
        assertTrue(body.containsKey("configuredSources"));
        assertTrue(body.containsKey("replicationSlot"));
        @SuppressWarnings("unchecked")
        Map<String, Object> slot = (Map<String, Object>) body.get("replicationSlot");
        assertEquals(true, slot.get("found"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) body.get("registeredSources");
        assertFalse(sources.isEmpty());
        assertEquals("postgres-debezium", sources.get(0).get("id"));
    }

    @Test
    void testSourcesListsPostgresDebezium() {
        ResponseEntity<List<Map<String, Object>>> response = cdcController.sources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> body = response.getBody();
        assertFalse(body.isEmpty());
        assertTrue(body.stream().anyMatch(s -> "postgres-debezium".equals(s.get("id"))));
    }

    @Test
    void testTargetsListsKafkaAndJdbcReplica() {
        ResponseEntity<List<Map<String, Object>>> response = cdcController.targets();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> body = response.getBody();
        assertEquals(2, body.size());
        assertTrue(body.stream().anyMatch(t -> "kafka".equals(t.get("id"))));
        assertTrue(body.stream().anyMatch(t -> "jdbc-replica".equals(t.get("id"))));
    }
}
