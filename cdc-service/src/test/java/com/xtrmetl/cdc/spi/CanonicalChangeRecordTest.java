package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalChangeRecordTest {

    @Test
    void snapshotsMutableInputMaps() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", "old");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "new");
        Map<String, Object> pk = new LinkedHashMap<>();
        pk.put("id", 7L);

        CanonicalChangeRecord record = new CanonicalChangeRecord(
                "postgres-debezium",
                "u",
                "public",
                "orders",
                123L,
                before,
                after,
                pk
        );
        int originalHashCode = record.hashCode();

        before.put("status", "mutated");
        after.clear();
        pk.put("id", 99L);

        assertEquals("old", record.getBefore().get("status"));
        assertEquals("new", record.getAfter().get("status"));
        assertEquals(7L, record.getPk().get("id"));
        assertEquals(originalHashCode, record.hashCode());
    }

    @Test
    void exposesUnmodifiableMaps() {
        CanonicalChangeRecord record = new CanonicalChangeRecord(
                "postgres-debezium",
                "c",
                "public",
                "orders",
                123L,
                Map.of(),
                Map.of("status", "new"),
                Map.of("id", 7L)
        );

        assertThrows(UnsupportedOperationException.class,
                () -> record.getAfter().put("status", "mutated"));
        assertThrows(UnsupportedOperationException.class,
                () -> record.getPk().remove("id"));
    }

    @Test
    void preservesNullEntriesForDatabaseRows() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("optional_column", null);

        CanonicalChangeRecord record = new CanonicalChangeRecord(
                "postgres-debezium",
                "c",
                "public",
                "orders",
                123L,
                null,
                after,
                null
        );

        assertTrue(record.getAfter().containsKey("optional_column"));
        assertNull(record.getAfter().get("optional_column"));
        assertTrue(record.getBefore().isEmpty());
        assertTrue(record.getPk().isEmpty());
    }
}
