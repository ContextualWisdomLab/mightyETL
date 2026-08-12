package com.xtrmetl.cdc.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Debezium JSON key/value envelopes into {@link CanonicalChangeRecord}.
 *
 * <p>Supports both envelope format ({@code payload.op/after/before/source}) and
 * unwrapped payloads. Used for any-to-any CDC scaffolding; live path still
 * publishes raw Debezium JSON to Kafka.</p>
 */
@Component
public class DebeziumChangeRecordMapper {

    private final ObjectMapper objectMapper;

    public DebeziumChangeRecordMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param sourceId logical source id (e.g. {@code postgres-debezium})
     * @param topic    Kafka / Debezium destination topic ({@code prefix.schema.table})
     * @param keyJson  optional Debezium key JSON
     * @param valueJson Debezium value JSON
     */
    public Optional<CanonicalChangeRecord> map(
            String sourceId,
            String topic,
            String keyJson,
            String valueJson
    ) {
        if (valueJson == null || valueJson.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(valueJson);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            if (payload == null || payload.isNull()) {
                return Optional.empty();
            }

            String op = textOrNull(payload, "op");
            JsonNode after = payload.get("after");
            JsonNode before = payload.get("before");
            if (op == null) {
                op = (after == null || after.isNull()) ? "d" : "u";
            }

            String schema = null;
            String table = null;
            long tsEpochMs = 0L;
            JsonNode source = payload.get("source");
            if (source != null && !source.isNull()) {
                schema = textOrNull(source, "schema");
                table = textOrNull(source, "table");
                if (source.has("ts_ms") && source.get("ts_ms").isNumber()) {
                    tsEpochMs = source.get("ts_ms").asLong();
                }
            }
            if (payload.has("ts_ms") && payload.get("ts_ms").isNumber() && tsEpochMs == 0L) {
                tsEpochMs = payload.get("ts_ms").asLong();
            }

            if (schema == null || table == null) {
                String[] fromTopic = schemaTableFromTopic(topic);
                if (schema == null) {
                    schema = fromTopic[0];
                }
                if (table == null) {
                    table = fromTopic[1];
                }
            }

            Map<String, Object> afterMap = toMap(after);
            Map<String, Object> beforeMap = toMap(before);
            Map<String, Object> pk = extractPk(keyJson);
            if (pk.isEmpty() && afterMap.containsKey("id")) {
                pk = Map.of("id", afterMap.get("id"));
            } else if (pk.isEmpty() && beforeMap.containsKey("id")) {
                pk = Map.of("id", beforeMap.get("id"));
            }

            return Optional.of(new CanonicalChangeRecord(
                    sourceId == null ? "unknown" : sourceId,
                    op,
                    schema,
                    table,
                    tsEpochMs,
                    beforeMap,
                    afterMap,
                    pk
            ));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> extractPk(String keyJson) {
        if (keyJson == null || keyJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(keyJson);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            return toMap(payload);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static String[] schemaTableFromTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return new String[]{null, null};
        }
        String[] parts = topic.split("\\.");
        if (parts.length >= 3) {
            return new String[]{parts[parts.length - 2], parts[parts.length - 1]};
        }
        if (parts.length == 2) {
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{null, parts[parts.length - 1]};
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                map.put(entry.getKey(), null);
            } else if (value.isNumber()) {
                map.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                map.put(entry.getKey(), value.booleanValue());
            } else if (value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            } else {
                map.put(entry.getKey(), value.toString());
            }
        }
        return map;
    }
}
