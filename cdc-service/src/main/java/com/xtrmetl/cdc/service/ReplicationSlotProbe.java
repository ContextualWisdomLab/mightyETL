package com.xtrmetl.cdc.service;

import com.xtrmetl.cdc.util.EnvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads PostgreSQL logical replication slot state for operator status (no secrets).
 *
 * <p>Uses the primary datasource ({@link JdbcTemplate}). Fail-open: DB errors become
 * {@code available=false} rather than failing the status API. Database-driver exception
 * details are intentionally excluded from both the returned status and ordinary logs.</p>
 */
@Service
public class ReplicationSlotProbe {

    private static final Logger log = LoggerFactory.getLogger(ReplicationSlotProbe.class);
    private static final String QUERY_FAILED_MESSAGE = "Replication slot state unavailable";

    // restart_lsn / confirmed_flush_lsn are pg_lsn; lag bytes via pg_wal_lsn_diff against current insert LSN.
    private static final String SLOT_SQL = """
            SELECT slot_name,
                   plugin,
                   slot_type,
                   active,
                   restart_lsn::text AS restart_lsn,
                   confirmed_flush_lsn::text AS confirmed_flush_lsn,
                   CASE
                     WHEN restart_lsn IS NULL THEN NULL
                     ELSE pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)
                   END AS restart_lag_bytes,
                   CASE
                     WHEN confirmed_flush_lsn IS NULL THEN NULL
                     ELSE pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)
                   END AS flush_lag_bytes
            FROM pg_replication_slots
            WHERE slot_name = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a replication-slot probe backed by the service's primary PostgreSQL datasource.
     *
     * @param jdbcTemplate JDBC access used for the read-only replication-slot query
     */
    public ReplicationSlotProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Probes the slot named by {@code CDC_SLOT_NAME}, or the legacy default when it is absent.
     *
     * @return a finite operator status map that never includes database-driver diagnostics
     */
    public Map<String, Object> probeConfiguredSlot() {
        String slotName = EnvUtils.getEnv("CDC_SLOT_NAME", "xtrmetl_slot");
        return probeSlot(slotName);
    }

    /**
     * Probes one PostgreSQL logical replication slot without exposing connection diagnostics.
     *
     * @param slotName replication slot to query
     * @return slot state when available, or a stable {@code query_failed} status when the query fails
     */
    public Map<String, Object> probeSlot(String slotName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slotName", slotName);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(SLOT_SQL, slotName);
            if (rows.isEmpty()) {
                result.put("available", true);
                result.put("found", false);
                result.put("active", false);
                result.put("message", "Slot not found (engine may not have created it yet)");
                return result;
            }
            Map<String, Object> row = rows.get(0);
            result.put("available", true);
            result.put("found", true);
            result.put("plugin", row.get("plugin"));
            result.put("slotType", row.get("slot_type"));
            result.put("active", row.get("active"));
            result.put("restartLsn", row.get("restart_lsn"));
            result.put("confirmedFlushLsn", row.get("confirmed_flush_lsn"));
            result.put("restartLagBytes", toLong(row.get("restart_lag_bytes")));
            result.put("flushLagBytes", toLong(row.get("flush_lag_bytes")));
            return result;
        } catch (DataAccessException e) {
            log.debug("Replication slot probe unavailable: query_failed");
            result.put("available", false);
            result.put("found", false);
            result.put("error", "query_failed");
            result.put("message", QUERY_FAILED_MESSAGE);
            return result;
        }
    }

    @Nullable
    private static Long toLong(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
