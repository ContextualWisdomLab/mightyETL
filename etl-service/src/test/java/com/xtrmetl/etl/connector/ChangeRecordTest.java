package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRecordTest {

    @Test
    void snapshotsMutableInputMaps() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", "old");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "new");
        Map<String, Object> pk = new LinkedHashMap<>();
        pk.put("id", 7L);

        ChangeRecord record = new ChangeRecord(
                "source",
                "u",
                "public",
                "orders",
                123L,
                before,
                after,
                pk
        );

        before.put("status", "mutated");
        after.clear();
        pk.put("id", 99L);

        assertEquals("old", record.getBefore().get("status"));
        assertEquals("new", record.getAfter().get("status"));
        assertEquals(7L, record.getPk().get("id"));
    }

    @Test
    void recursivelySnapshotsNestedJsonContainers() {
        List<Object> lineItems = new ArrayList<>();
        lineItems.add("first");
        lineItems.add(null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("line_items", lineItems);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("details", details);

        ChangeRecord record = new ChangeRecord(
                "source",
                "u",
                "public",
                "orders",
                123L,
                Map.of(),
                after,
                Map.of("id", 7L)
        );

        lineItems.set(0, "mutated");
        lineItems.add("late");
        details.put("late_field", "mutated");

        Map<?, ?> snapshottedDetails = (Map<?, ?>) record.getAfter().get("details");
        List<?> snapshottedLineItems = (List<?>) snapshottedDetails.get("line_items");
        assertEquals(2, snapshottedLineItems.size());
        assertEquals("first", snapshottedLineItems.get(0));
        assertNull(snapshottedLineItems.get(1));
        assertTrue(!snapshottedDetails.containsKey("late_field"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void exposesNestedJsonContainersAsUnmodifiable() {
        List<Object> lineItems = new ArrayList<>();
        lineItems.add("first");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("line_items", lineItems);

        ChangeRecord record = new ChangeRecord(
                "source",
                "c",
                "public",
                "orders",
                123L,
                Map.of(),
                Map.of("details", details),
                Map.of("id", 7L)
        );

        Map nestedMap = (Map) record.getAfter().get("details");
        List nestedList = (List) nestedMap.get("line_items");
        assertThrows(UnsupportedOperationException.class,
                () -> nestedMap.put("late_field", "mutated"));
        assertThrows(UnsupportedOperationException.class,
                () -> nestedList.add("mutated"));
    }

    @Test
    void exposesUnmodifiableMaps() {
        ChangeRecord record = new ChangeRecord(
                "source",
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

        ChangeRecord record = new ChangeRecord(
                "source",
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
