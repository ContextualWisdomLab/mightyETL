package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ProcessedDataReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(ProcessedDataReplicaApplier.class);

    private static final String TABLE_NAME = "processed_data";

    private static final String UPSERT_SQL = """
            INSERT INTO processed_data (id, data)
            VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data
            """.strip();

    private static final String DELETE_SQL = "DELETE FROM processed_data WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProcessedDataReplicaApplier(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void apply(String topic, String keyJson, String valueJson) {
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

        String data = extractTextOrJson(envelope.after(), "data");
        if (data == null) {
            log.debug("Skipping replica apply: missing data (topic={}, id={})", topic, id);
            return;
        }

        jdbcTemplate.update(UPSERT_SQL, id, data);
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
            log.debug("Failed to parse Debezium value JSON; skipping replica apply", e);
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

    private static String extractTextOrJson(JsonNode object, String fieldName) {
        if (object == null || object.isNull()) {
            return null;
        }

        JsonNode value = object.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isTextual()) {
            return value.asText();
        }

        return value.toString();
    }

    private record DebeziumEnvelope(String op, JsonNode after) {}
}
