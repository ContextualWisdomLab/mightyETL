package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.cdc.config.XtrmetlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Applies CDC events for configured tables that share the {@code processed_data} row shape
 * ({@code id}, {@code data}). Table names are validated as SQL identifiers only.
 */
@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ProcessedDataReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(ProcessedDataReplicaApplier.class);

    private static final Pattern SAFE_TABLE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedTables;

    public ProcessedDataReplicaApplier(
            @Qualifier("replicaJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            XtrmetlProperties properties
    ) {
        this(jdbcTemplate, objectMapper, parseTables(properties.getReplica().getTables()));
    }

    /**
     * Test-friendly constructor.
     */
    ProcessedDataReplicaApplier(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Set<String> allowedTables
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.allowedTables = Set.copyOf(allowedTables);
        if (this.allowedTables.isEmpty()) {
            throw new IllegalArgumentException("xtrmetl.replica.tables must list at least one table");
        }
    }

    public void apply(@Nullable String topic, @Nullable String keyJson, @Nullable String valueJson) {
        if (topic == null) {
            return;
        }

        String table = tableFromTopic(topic);
        if (table == null || !allowedTables.contains(table)) {
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
            jdbcTemplate.update(deleteSql(table), id);
            return;
        }

        JsonNode after = envelope.after();
        if (after == null || after.isNull() || !after.has("data")) {
            log.error("Replica apply failed: missing {}.data field (topic={}, id={})", table, topic, id);
            throw new IllegalStateException("Missing " + table + ".data field in CDC event for id=" + id);
        }

        JsonNode dataNode = after.get("data");
        if (dataNode.isNull()) {
            log.error("Replica apply failed: {}.data is null (topic={}, id={})", table, topic, id);
            throw new IllegalStateException(table + ".data is null in CDC event for id=" + id);
        }

        String data = dataNode.isTextual() ? dataNode.asText() : dataNode.toString();
        // created_at is set by the replica DB on insert (DEFAULT) and intentionally not updated on upserts.
        jdbcTemplate.update(Objects.requireNonNull(upsertSql(table)), id, data);
    }

    Set<String> allowedTables() {
        return allowedTables;
    }

    static Set<String> parseTables(String tablesConfig) {
        if (tablesConfig == null || tablesConfig.isBlank()) {
            return Set.of("processed_data");
        }
        Set<String> tables = Arrays.stream(tablesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    if (!SAFE_TABLE.matcher(s).matches()) {
                        throw new IllegalArgumentException(
                                "Invalid replica table name '" + s + "': must match " + SAFE_TABLE.pattern()
                        );
                    }
                    return s;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return tables.isEmpty() ? Set.of("processed_data") : tables;
    }

    private static String tableFromTopic(String topic) {
        int lastDot = topic.lastIndexOf('.');
        if (lastDot < 0 || lastDot == topic.length() - 1) {
            return null;
        }
        return topic.substring(lastDot + 1);
    }

    private static String upsertSql(String table) {
        // table already validated against SAFE_TABLE
        return """
                INSERT INTO %s (id, data)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data
                """.formatted(table).strip();
    }

    private static String deleteSql(String table) {
        return "DELETE FROM " + table + " WHERE id = ?";
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
