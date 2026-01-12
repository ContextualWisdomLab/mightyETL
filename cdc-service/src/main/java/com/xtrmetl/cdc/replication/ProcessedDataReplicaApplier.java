package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ProcessedDataReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(ProcessedDataReplicaApplier.class);

    private static final String TABLE_NAME = "processed_data";

    // created_at is set by the replica DB on insert (DEFAULT) and intentionally not updated on upserts.
    // This replication path currently tracks only the latest `data` value; it does not maintain an `updated_at`.
    private static final String UPSERT_SQL = """
            INSERT INTO processed_data (id, data)
            VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data
            """.strip();

    private static final String DELETE_SQL = "DELETE FROM processed_data WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProcessedDataReplicaApplier(
            @Qualifier("replicaJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void apply(@Nullable String topic, @Nullable String keyJson, @Nullable String valueJson) {
        if (topic == null || !topic.endsWith("." + TABLE_NAME)) {
            return;
        }

        DebeziumEnvelope envelope = parseDebeziumEnvelope(valueJson);
        if (envelope == null) {
            return;
        }

        Long id = extractId(keyJson, envelope.after());
        if (id == null) {
            log.debug("Skipping replica apply: missing id (topic={})", topic);
            return;
        }

        String op = envelope.op();
        if ("d".equals(op)) {
            jdbcTemplate.update(DELETE_SQL, id);
            return;
        }

        JsonNode after = envelope.after();
        if (after == null || after.isNull() || !after.has("data")) {
            log.error("Replica apply failed: missing processed_data.data field (topic={}, id={})", topic, id);
            throw new IllegalStateException("Missing processed_data.data field in CDC event for id=" + id);
        }

        JsonNode dataNode = after.get("data");
        if (dataNode.isNull()) {
            log.error("Replica apply failed: processed_data.data is null (topic={}, id={})", topic, id);
            throw new IllegalStateException("processed_data.data is null in CDC event for id=" + id);
        }

        String data = dataNode.isTextual() ? dataNode.asText() : dataNode.toString();
        jdbcTemplate.update(Objects.requireNonNull(UPSERT_SQL), id, data);
    }

    private DebeziumEnvelope parseDebeziumEnvelope(String valueJson) {
        if (valueJson == null || valueJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(valueJson);
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                return null;
            }

            String op = payload.path("op").asText(null);
            JsonNode after = payload.get("after");

            if (op == null) {
                op = after == null || after.isNull() ? "d" : "u";
            }

            return new DebeziumEnvelope(op, after);
        } catch (IOException e) {
            log.warn("Failed to parse Debezium value JSON; skipping replica apply", e);
            return null;
        }
    }

    private Long extractId(String keyJson, JsonNode after) {
        Long idFromKey = extractIdFromKey(keyJson);
        if (idFromKey != null) {
            return idFromKey;
        }

        return extractLong(after, "id");
    }

    private Long extractIdFromKey(String keyJson) {
        if (keyJson == null || keyJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(keyJson);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            return extractLong(payload, "id");
        } catch (IOException e) {
            log.debug("Failed to parse Debezium key JSON; falling back to value payload", e);
            return null;
        }
    }

    private static Long extractLong(JsonNode object, String fieldName) {
        if (object == null || object.isNull()) {
            return null;
        }

        JsonNode value = object.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isNumber()) {
            return value.longValue();
        }

        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private record DebeziumEnvelope(String op, JsonNode after) {}
}
