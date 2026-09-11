package com.xtrmetl.cdc.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable product-neutral change event for any-to-any CDC routing.
 *
 * <p>The constructor takes shallow snapshots of the supplied row maps so later
 * caller mutations cannot change this record's contents, equality, or hash
 * identity. Null map references become empty maps, while null database values
 * inside a supplied map are preserved. Debezium envelopes are adapted via
 * {@link DebeziumChangeRecordMapper}. The canonical form is not yet wired into
 * the live Kafka publication path and remains the planned multi-target routing
 * value object.</p>
 */
public final class CanonicalChangeRecord {

    private final String sourceId;
    private final String op;
    private final String schema;
    private final String table;
    private final long tsEpochMs;
    private final Map<String, Object> before;
    private final Map<String, Object> after;
    private final Map<String, Object> pk;

    /**
     * Creates an immutable canonical CDC record from scalar metadata and row snapshots.
     *
     * @param sourceId stable source connector identifier
     * @param op Debezium-style operation code
     * @param schema source database schema, when available
     * @param table source table name, when available
     * @param tsEpochMs source event timestamp in epoch milliseconds
     * @param before row values before the change, or {@code null} when unavailable
     * @param after row values after the change, or {@code null} when unavailable
     * @param pk primary-key values, or {@code null} when unavailable
     */
    public CanonicalChangeRecord(
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

    private static Map<String, Object> snapshot(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * Returns the stable source connector identifier.
     *
     * @return source connector identifier
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Returns the source operation code.
     *
     * @return operation code
     */
    public String getOp() {
        return op;
    }

    /**
     * Returns the source database schema, when available.
     *
     * @return source schema
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Returns the source table, when available.
     *
     * @return source table
     */
    public String getTable() {
        return table;
    }

    /**
     * Returns the source event timestamp in epoch milliseconds.
     *
     * @return source event timestamp
     */
    public long getTsEpochMs() {
        return tsEpochMs;
    }

    /**
     * Returns the immutable construction-time snapshot of values before the change.
     *
     * @return immutable before-values map
     */
    public Map<String, Object> getBefore() {
        return before;
    }

    /**
     * Returns the immutable construction-time snapshot of values after the change.
     *
     * @return immutable after-values map
     */
    public Map<String, Object> getAfter() {
        return after;
    }

    /**
     * Returns the immutable construction-time snapshot of primary-key values.
     *
     * @return immutable primary-key map
     */
    public Map<String, Object> getPk() {
        return pk;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CanonicalChangeRecord that)) {
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
}
