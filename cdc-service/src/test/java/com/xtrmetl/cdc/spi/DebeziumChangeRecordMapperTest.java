package com.xtrmetl.cdc.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebeziumChangeRecordMapperTest {

    private DebeziumChangeRecordMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DebeziumChangeRecordMapper(new ObjectMapper());
    }

    @Test
    void mapsEnvelopeCreateEvent() {
        String key = """
                {"payload":{"id":42}}
                """;
        String value = """
                {
                  "payload": {
                    "op": "c",
                    "before": null,
                    "after": {"id": 42, "data": "hello"},
                    "source": {"schema": "public", "table": "processed_data", "ts_ms": 1700000000000},
                    "ts_ms": 1700000000001
                  }
                }
                """;

        Optional<CanonicalChangeRecord> result = mapper.map(
                "postgres-debezium",
                "xtrmetl-cdc.public.processed_data",
                key,
                value
        );

        assertTrue(result.isPresent());
        CanonicalChangeRecord record = result.get();
        assertEquals("c", record.getOp());
        assertEquals("public", record.getSchema());
        assertEquals("processed_data", record.getTable());
        assertEquals(1700000000000L, record.getTsEpochMs());
        assertEquals(42, ((Number) record.getAfter().get("id")).intValue());
        assertEquals("hello", record.getAfter().get("data"));
        assertEquals(42, ((Number) record.getPk().get("id")).intValue());
    }

    @Test
    void mapsDeleteUsingTopicWhenSourceMissing() {
        String value = """
                {
                  "payload": {
                    "op": "d",
                    "before": {"id": 7},
                    "after": null
                  }
                }
                """;

        Optional<CanonicalChangeRecord> result = mapper.map(
                "postgres-debezium",
                "xtrmetl-cdc.public.orders",
                null,
                value
        );

        assertTrue(result.isPresent());
        CanonicalChangeRecord record = result.get();
        assertEquals("d", record.getOp());
        assertEquals("public", record.getSchema());
        assertEquals("orders", record.getTable());
        assertEquals(7, ((Number) record.getPk().get("id")).intValue());
    }

    @Test
    void malformedKeyFallsBackToValidAfterIdentifierWithoutDroppingTheEvent() {
        String value = """
                {
                  "payload": {
                    "op": "u",
                    "before": {"id": 41, "data": "old"},
                    "after": {"id": 42, "data": "new"},
                    "source": {"schema": "public", "table": "processed_data"}
                  }
                }
                """;

        Optional<CanonicalChangeRecord> result = mapper.map(
                "postgres-debezium",
                "xtrmetl-cdc.public.processed_data",
                "{malformed-key-json",
                value
        );

        assertTrue(result.isPresent(), "a malformed optional key must not discard an otherwise valid CDC value");
        CanonicalChangeRecord record = result.get();
        assertEquals("u", record.getOp());
        assertEquals(42, ((Number) record.getPk().get("id")).intValue());
        assertEquals("new", record.getAfter().get("data"));
    }

    @Test
    void emptyValueReturnsEmpty() {
        assertTrue(mapper.map("s", "t", null, null).isEmpty());
        assertTrue(mapper.map("s", "t", null, "  ").isEmpty());
    }
}
