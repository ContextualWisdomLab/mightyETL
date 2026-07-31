package com.xtrmetl.cdc.health;

import com.xtrmetl.cdc.service.CdcService;
import com.xtrmetl.cdc.service.ReplicationSlotProbe;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator health contribution for the embedded CDC engine and replication slot probe.
 *
 * <ul>
 *   <li>UP when the engine is running</li>
 *   <li>UP (idle) when not running and autostart is disabled</li>
 *   <li>DOWN when not running but autostart expects a live engine</li>
 * </ul>
 *
 * <p>Slot probe failures do not alone mark DOWN (permissions / cold start); they appear as details.</p>
 */
@Component("cdcEngine")
public class CdcEngineHealthIndicator implements HealthIndicator {

    private final CdcService cdcService;
    private final ReplicationSlotProbe replicationSlotProbe;

    public CdcEngineHealthIndicator(CdcService cdcService, ReplicationSlotProbe replicationSlotProbe) {
        this.cdcService = cdcService;
        this.replicationSlotProbe = replicationSlotProbe;
    }

    @Override
    public Health health() {
        boolean running = cdcService.isRunning();
        boolean autoStart = cdcService.isAutoStart();
        Map<String, Object> slot = replicationSlotProbe.probeConfiguredSlot();

        Health.Builder builder;
        if (running) {
            builder = Health.up();
        } else if (!autoStart) {
            builder = Health.up().withDetail("idle", true);
        } else {
            builder = Health.down().withDetail("reason", "engine_not_running");
        }

        builder.withDetail("product", "mightyETL")
                .withDetail("running", running)
                .withDetail("autoStart", autoStart)
                .withDetail("sourceType", "postgres-debezium")
                .withDetail("replicationSlot", slot);

        Object active = slot.get("active");
        if (running && Boolean.FALSE.equals(active) && Boolean.TRUE.equals(slot.get("found"))) {
            builder.withDetail("slotWarning", "engine_running_but_slot_inactive");
        }
        if (running && Boolean.FALSE.equals(slot.get("found")) && Boolean.TRUE.equals(slot.get("available"))) {
            builder.withDetail("slotWarning", "slot_not_found_yet");
        }

        return builder.build();
    }
}
