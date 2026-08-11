package com.xtrmetl.etl.connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized change event for target connectors (canonical CDC/ETL record).
 *
 * <p>Row maps are recursively snapshotted at construction time so later caller mutations to
 * JSON-shaped {@link Map} and {@link List} containers cannot alter a record after it has entered
 * a connector pipeline. Nested containers exposed through this record are unmodifiable. Null
 * database values are preserved, and values that are not maps or lists keep their original object
 * identity. Cyclic map/list graphs are rejected deterministically rather than recursing until the
 * JVM stack is exhausted.</p>
 */
public final class ChangeRecord {

    private static final String CYCLIC_CONTAINER_MESSAGE =
            "ChangeRecord JSON containers must not contain cycles";

    private final String sourceId;
    private final String op;
    private final String schema;
    private final String table;
    private final long tsEpochMs;
    private final Map<String, Object> before;
    private final Map<String, Object> after;
    private final Map<String, Object> pk;

    /**
     * Creates an immutable snapshot of one normalized connector change.
     *
     * @param sourceId stable identifier of the source connector
     * @param op change operation such as create, update, or delete
     * @param schema source schema name
     * @param table source table name
     * @param tsEpochMs source event timestamp in epoch milliseconds
     * @param before row values before the change, or {@code null} when unavailable
     * @param after row values after the change, or {@code null} when unavailable
     * @param pk primary-key values for the changed row, or {@code null} when unavailable
     * @throws IllegalArgumentException when a map/list container graph contains a cycle
     */
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

    /**
     * Returns the stable identifier of the source connector.
     *
     * @return source connector identifier
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Returns the normalized change operation.
     *
     * @return change operation
     */
    public String getOp() {
        return op;
    }

    /**
     * Returns the source schema name.
     *
     * @return source schema name
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Returns the source table name.
     *
     * @return source table name
     */
    public String getTable() {
        return table;
    }

    /**
     * Returns the source event timestamp in epoch milliseconds.
     *
     * @return event timestamp in epoch milliseconds
     */
    public long getTsEpochMs() {
        return tsEpochMs;
    }

    /**
     * Returns the immutable row snapshot from before the change.
     *
     * @return unmodifiable before-image map, empty when unavailable
     */
    public Map<String, Object> getBefore() {
        return before;
    }

    /**
     * Returns the immutable row snapshot from after the change.
     *
     * @return unmodifiable after-image map, empty when unavailable
     */
    public Map<String, Object> getAfter() {
        return after;
    }

    /**
     * Returns the immutable primary-key snapshot for the changed row.
     *
     * @return unmodifiable primary-key map, empty when unavailable
     */
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
        IdentityHashMap<Object, Boolean> activeContainers = new IdentityHashMap<>();
        enterContainer(source, activeContainers);
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, snapshotValue(value, activeContainers)));
            return Collections.unmodifiableMap(copy);
        } finally {
            activeContainers.remove(source);
        }
    }

    private static Object snapshotValue(
            Object value,
            IdentityHashMap<Object, Boolean> activeContainers
    ) {
        if (value instanceof Map<?, ?> nestedMap) {
            enterContainer(nestedMap, activeContainers);
            try {
                Map<Object, Object> copy = new LinkedHashMap<>();
                nestedMap.forEach((key, nestedValue) ->
                        copy.put(key, snapshotValue(nestedValue, activeContainers)));
                return Collections.unmodifiableMap(copy);
            } finally {
                activeContainers.remove(nestedMap);
            }
        }
        if (value instanceof List<?> nestedList) {
            enterContainer(nestedList, activeContainers);
            try {
                List<Object> copy = new ArrayList<>(nestedList.size());
                nestedList.forEach(element -> copy.add(snapshotValue(element, activeContainers)));
                return Collections.unmodifiableList(copy);
            } finally {
                activeContainers.remove(nestedList);
            }
        }
        return value;
    }

    private static void enterContainer(
            Object container,
            IdentityHashMap<Object, Boolean> activeContainers
    ) {
        if (activeContainers.put(container, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(CYCLIC_CONTAINER_MESSAGE);
        }
    }
}
