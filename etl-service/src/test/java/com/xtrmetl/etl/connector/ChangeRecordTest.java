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
    void recursivelySnapshotsJsonShapedContainers() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Seoul");
        List<Object> tags = new ArrayList<>();
        tags.add("priority");
        tags.add(null);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("address", address);
        after.put("tags", tags);

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

        address.put("city", "Busan");
        tags.set(0, "mutated");
        tags.add("late");

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshottedAddress =
                (Map<String, Object>) record.getAfter().get("address");
        @SuppressWarnings("unchecked")
        List<Object> snapshottedTags = (List<Object>) record.getAfter().get("tags");

        assertEquals("Seoul", snapshottedAddress.get("city"));
        assertEquals(List.of("priority", null), snapshottedTags);
    }

    @Test
    void nestedSnapshotContainersAreUnmodifiable() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Seoul");
        List<Object> tags = new ArrayList<>();
        tags.add("priority");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("address", address);
        after.put("tags", tags);

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

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshottedAddress =
                (Map<String, Object>) record.getAfter().get("address");
        @SuppressWarnings("unchecked")
        List<Object> snapshottedTags = (List<Object>) record.getAfter().get("tags");

        assertThrows(UnsupportedOperationException.class,
                () -> snapshottedAddress.put("city", "Busan"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshottedTags.add("late"));
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
