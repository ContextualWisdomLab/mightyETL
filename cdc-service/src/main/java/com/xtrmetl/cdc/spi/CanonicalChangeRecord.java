package com.xtrmetl.cdc.spi;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Product-neutral change event for any-to-any routing (source → targets).
 *
 * <p>Debezium envelopes are adapted via {@link DebeziumChangeRecordMapper}.
 * Not yet wired into the live Kafka publish path — canonical form is for
 * upcoming multi-target routing.</p>
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
        this.before = before == null ? Map.of() : Collections.unmodifiableMap(before);
        this.after = after == null ? Map.of() : Collections.unmodifiableMap(after);
        this.pk = pk == null ? Map.of() : Collections.unmodifiableMap(pk);
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
