package com.xtrmetl.cdc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that operator replication-slot failures keep database diagnostics confidential.
 */
@ExtendWith(OutputCaptureExtension.class)
class ReplicationSlotProbeConfidentialityTest {

    @Test
    void failureResponseAndOrdinaryLogsExcludeDriverConnectionDiagnostics(CapturedOutput output) {
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
        String logs = output.getOut() + output.getErr();
        assertFalse(logs.contains("super-secret"));
        assertFalse(logs.contains("jdbc:postgresql://"));
    }
}
