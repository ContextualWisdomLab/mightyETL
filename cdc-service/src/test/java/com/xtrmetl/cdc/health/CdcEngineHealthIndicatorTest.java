package com.xtrmetl.cdc.health;

import com.xtrmetl.cdc.service.CdcService;
import com.xtrmetl.cdc.service.ReplicationSlotProbe;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CdcEngineHealthIndicatorTest {

    @Test
    void upWhenEngineRunning() {
        CdcService service = mock(CdcService.class);
        when(service.isRunning()).thenReturn(true);
        when(service.isAutoStart()).thenReturn(true);
        ReplicationSlotProbe probe = mock(ReplicationSlotProbe.class);
        when(probe.probeConfiguredSlot()).thenReturn(Map.of("found", true, "active", true, "available", true));

        Health health = new CdcEngineHealthIndicator(service, probe).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("running"));
        assertEquals("mightyETL", health.getDetails().get("product"));
        assertEquals("postgres-debezium", health.getDetails().get("sourceType"));
    }

    @Test
    void downWhenExpectedRunningButStopped() {
        CdcService service = mock(CdcService.class);
        when(service.isRunning()).thenReturn(false);
        when(service.isAutoStart()).thenReturn(true);
        ReplicationSlotProbe probe = mock(ReplicationSlotProbe.class);
        when(probe.probeConfiguredSlot()).thenReturn(Map.of("available", false));

        Health health = new CdcEngineHealthIndicator(service, probe).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("engine_not_running", health.getDetails().get("reason"));
    }

    @Test
    void upIdleWhenAutostartDisabledAndStopped() {
        CdcService service = mock(CdcService.class);
        when(service.isRunning()).thenReturn(false);
        when(service.isAutoStart()).thenReturn(false);
        ReplicationSlotProbe probe = mock(ReplicationSlotProbe.class);
        when(probe.probeConfiguredSlot()).thenReturn(Map.of("available", true, "found", false));

        Health health = new CdcEngineHealthIndicator(service, probe).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("idle"));
    }
}
