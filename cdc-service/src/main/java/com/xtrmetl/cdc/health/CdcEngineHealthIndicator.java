package com.xtrmetl.cdc.health;

import com.xtrmetl.cdc.service.CdcService;
import com.xtrmetl.cdc.service.ReplicationSlotProbe;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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
 * <p>Slot probe failures do not alone mark DOWN (permissions / cold start); they appear as
 * finite, purpose-bound details. Low-level slot identifiers and provider diagnostics are not
 * republished through Actuator health.</p>
 */
@Component("cdcEngine")
public class CdcEngineHealthIndicator implements HealthIndicator {

    private static final String QUERY_FAILED = "query_failed";

    private final CdcService cdcService;
    private final ReplicationSlotProbe replicationSlotProbe;

    /**
     * Creates the CDC health contributor.
     *
     * @param cdcService service that reports whether the embedded CDC engine is expected and running
     * @param replicationSlotProbe probe used to derive bounded replication-slot health state
     */
    public CdcEngineHealthIndicator(CdcService cdcService, ReplicationSlotProbe replicationSlotProbe) {
        this.cdcService = cdcService;
        this.replicationSlotProbe = replicationSlotProbe;
    }

    /**
     * Returns the current CDC health state with a fixed, non-sensitive replication-slot projection.
     *
     * <p>The complete probe result remains available only inside this method for warning decisions;
     * the public health details include only known booleans, lag measurements, and the stable
     * {@code query_failed} classification.</p>
     *
     * @return current CDC health status and purpose-bound operator details
     */
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
                .withDetail("replicationSlot", publicReplicationSlotDetails(slot));

        Object active = slot.get("active");
        if (running && Boolean.FALSE.equals(active) && Boolean.TRUE.equals(slot.get("found"))) {
            builder.withDetail("slotWarning", "engine_running_but_slot_inactive");
        }
        if (running && Boolean.FALSE.equals(slot.get("found")) && Boolean.TRUE.equals(slot.get("available"))) {
            builder.withDetail("slotWarning", "slot_not_found_yet");
        }

        return builder.build();
    }

    private static Map<String, Object> publicReplicationSlotDetails(Map<String, Object> slot) {
        Map<String, Object> details = new LinkedHashMap<>();
        copyBoolean(slot, details, "available");
        copyBoolean(slot, details, "found");
        copyBoolean(slot, details, "active");
        copyLong(slot, details, "restartLagBytes");
        copyLong(slot, details, "flushLagBytes");
        if (QUERY_FAILED.equals(slot.get("error"))) {
            details.put("error", QUERY_FAILED);
        }
        return Map.copyOf(details);
    }

    private static void copyBoolean(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value instanceof Boolean) {
            target.put(key, value);
        }
    }

    private static void copyLong(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value instanceof Long) {
            target.put(key, value);
        }
    }
}
