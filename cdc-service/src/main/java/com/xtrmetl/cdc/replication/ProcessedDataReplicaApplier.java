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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Applies CDC events for configured tables that share the {@code processed_data} row shape
 * ({@code id}, {@code data}). Table names are compiled from validated configuration only.
 */
@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ProcessedDataReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(ProcessedDataReplicaApplier.class);

    private static final Pattern SAFE_TABLE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedTables;
    private final Map<String, TableSql> sqlByTable;

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
        this.allowedTables = allowedTables.stream()
                .map(ProcessedDataReplicaApplier::requireSafeTable)
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedTables.isEmpty()) {
            throw new IllegalArgumentException("xtrmetl.replica.tables must list at least one table");
        }

        Map<String, TableSql> compiledSql = new LinkedHashMap<>();
        for (String table : this.allowedTables) {
            compiledSql.put(table, new TableSql(upsertSql(table), deleteSql(table)));
        }
        this.sqlByTable = Map.copyOf(compiledSql);
    }

    public void apply(@Nullable String topic, @Nullable String keyJson, @Nullable String valueJson) {
        if (topic == null) {
            return;
        }

        TableSql tableSql = sqlByTable.get(tableFromTopic(topic));
        if (tableSql == null) {
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
            // JDBC cannot bind identifiers. This SQL was precompiled from the constructor-validated
            // table allow-list; the public topic input can only select an existing map entry.
            jdbcTemplate.update(tableSql.deleteSql(), id); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
            return;
        }

        JsonNode after = envelope.after();
        if (after == null || after.isNull() || !after.has("data")) {
            log.error("Replica apply failed: missing data field (topic={}, id={})", topic, id);
            throw new IllegalStateException("Missing data field in CDC event for id=" + id);
        }

        JsonNode dataNode = after.get("data");
        if (dataNode.isNull()) {
            log.error("Replica apply failed: data is null (topic={}, id={})", topic, id);
            throw new IllegalStateException("data is null in CDC event for id=" + id);
        }

        String data = dataNode.isTextual() ? dataNode.asText() : dataNode.toString();
        // created_at is set by the replica DB on insert (DEFAULT) and intentionally not updated on upserts.
        // The statement is precompiled from validated configuration; id/data remain JDBC-bound values.
        jdbcTemplate.update(tableSql.upsertSql(), id, data); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
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
                .map(ProcessedDataReplicaApplier::requireSafeTable)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return tables.isEmpty() ? Set.of("processed_data") : tables;
    }

    private static String requireSafeTable(String table) {
        if (table == null || !SAFE_TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException(
                    "Invalid replica table name '" + table + "': must match " + SAFE_TABLE.pattern()
            );
        }
        return table;
    }

    private static String tableFromTopic(String topic) {
        int lastDot = topic.lastIndexOf('.');
        if (lastDot < 0 || lastDot == topic.length() - 1) {
            return null;
        }
        return topic.substring(lastDot + 1);
    }

    private static String upsertSql(String table) {
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

    private record TableSql(String upsertSql, String deleteSql) {}

    private record DebeziumEnvelope(String op, JsonNode after) {}
}
