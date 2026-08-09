package com.xtrmetl.cdc.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplicationSlotProbeTest {

    @Test
    void reportsFoundActiveSlotWithLag() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq("xtrmetl_slot"))).thenReturn(List.of(Map.of(
                "plugin", "pgoutput",
                "slot_type", "logical",
                "active", true,
                "restart_lsn", "0/16B6C50",
                "confirmed_flush_lsn", "0/16B6C80",
                "restart_lag_bytes", 4096L,
                "flush_lag_bytes", 1024L
        )));

        Map<String, Object> result = new ReplicationSlotProbe(jdbc).probeSlot("xtrmetl_slot");

        assertEquals(true, result.get("available"));
        assertEquals(true, result.get("found"));
        assertEquals(true, result.get("active"));
        assertEquals(4096L, result.get("restartLagBytes"));
        assertEquals(1024L, result.get("flushLagBytes"));
    }

    @Test
    void reportsNotFoundWhenEmpty() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq("missing"))).thenReturn(List.of());

        Map<String, Object> result = new ReplicationSlotProbe(jdbc).probeSlot("missing");

        assertTrue((Boolean) result.get("available"));
        assertFalse((Boolean) result.get("found"));
    }

    @Test
    void failOpenOnDataAccessErrorWithoutExposingDriverDiagnostics() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String sensitiveDriverMessage =
                "jdbc:postgresql://db.internal:5432/prod?user=admin&password=super-secret";
        when(jdbc.queryForList(anyString(), eq("xtrmetl_slot")))
                .thenThrow(new DataAccessResourceFailureException(sensitiveDriverMessage));

        Map<String, Object> result = new ReplicationSlotProbe(jdbc).probeSlot("xtrmetl_slot");

        assertFalse((Boolean) result.get("available"));
        assertEquals("query_failed", result.get("error"));
        assertEquals("Replication slot state unavailable", result.get("message"));
        assertFalse(result.toString().contains("super-secret"));
        assertFalse(result.toString().contains("jdbc:postgresql://"));
    }
}
