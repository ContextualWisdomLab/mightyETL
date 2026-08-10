package com.xtrmetl.etl.connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized change event for target connectors (canonical CDC/ETL record).
 *
 * <p>JSON-shaped row maps and nested map/list containers are recursively snapshotted at
 * construction time so later caller mutations cannot change a record that has entered a
 * connector pipeline. Null database values remain supported. Scalar and other non-container
 * values are retained as supplied; this type does not claim to clone arbitrary mutable Java
 * objects.</p>
 */
public final class ChangeRecord {

    private final String sourceId;
    private final String op;
    private final String schema;
    private final String table;
    private final long tsEpochMs;
    private final Map<String, Object> before;
    private final Map<String, Object> after;
    private final Map<String, Object> pk;

    public ChangeRecord(
            String sourceId,
            String op,
            String schema,
            String table,
            long tsEpochMs,
            Map<String, Object> before,
            Map<String, Object> after,
            Map<String, Object> pk
    ) {
        this.sourceId = sourceId;
        this.op = op;
        this.schema = schema;
        this.table = table;
        this.tsEpochMs = tsEpochMs;
        this.before = snapshot(before);
        this.after = snapshot(after);
        this.pk = snapshot(pk);
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getOp() {
        return op;
    }

    public String getSchema() {
        return schema;
    }

    public String getTable() {
        return table;
    }

    public long getTsEpochMs() {
        return tsEpochMs;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public Map<String, Object> getPk() {
        return pk;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChangeRecord that)) {
            return false;
        }
        return tsEpochMs == that.tsEpochMs
                && Objects.equals(sourceId, that.sourceId)
                && Objects.equals(op, that.op)
                && Objects.equals(schema, that.schema)
                && Objects.equals(table, that.table)
                && Objects.equals(before, that.before)
                && Objects.equals(after, that.after)
                && Objects.equals(pk, that.pk);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceId, op, schema, table, tsEpochMs, before, after, pk);
    }

    private static Map<String, Object> snapshot(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, snapshotValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object snapshotValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> copy.put(key, snapshotValue(nestedValue)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> listValue) {
            List<Object> copy = new ArrayList<>(listValue.size());
            for (Object element : listValue) {
                copy.add(snapshotValue(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
