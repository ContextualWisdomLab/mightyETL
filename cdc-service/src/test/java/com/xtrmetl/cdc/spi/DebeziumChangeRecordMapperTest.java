package com.xtrmetl.cdc.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void emptyValueReturnsEmpty() {
        assertTrue(mapper.map("s", "t", null, null).isEmpty());
        assertTrue(mapper.map("s", "t", null, "  ").isEmpty());
    }

    @Test
    void preservesJsonPropertyOrderAndMappedValueTypes() {
        String value = """
                {
                  "payload": {
                    "op": "c",
                    "after": {
                      "null_value": null,
                      "numeric_value": 42.5,
                      "boolean_value": true,
                      "text_value": "hello",
                      "nested_value": {"inner": "value"}
                    }
                  }
                }
                """;

        CanonicalChangeRecord record = mapper.map(
                "postgres-debezium",
                "xtrmetl-cdc.public.typed_values",
                null,
                value
        ).orElseThrow();
        Map<String, Object> after = record.getAfter();

        assertEquals(
                List.of(
                        "null_value",
                        "numeric_value",
                        "boolean_value",
                        "text_value",
                        "nested_value"
                ),
                new ArrayList<>(after.keySet())
        );
        assertTrue(after.containsKey("null_value"));
        assertNull(after.get("null_value"));
        assertEquals(42.5d, ((Number) after.get("numeric_value")).doubleValue());
        assertEquals(true, after.get("boolean_value"));
        assertEquals("hello", after.get("text_value"));
        assertEquals("{\"inner\":\"value\"}", after.get("nested_value"));
    }

    @Test
    void productionMapperUsesSupportedJacksonPropertyIterationApi() throws IOException {
        String source = Files.readString(
                projectRoot().resolve("cdc-service/src/main/java/com/xtrmetl/cdc/spi/DebeziumChangeRecordMapper.java"),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("node.fields()"),
                "JsonNode.fields() is deprecated since Jackson 2.19 and must not remain in production CDC code");
        assertTrue(source.contains("node.properties()"),
                "CDC object-node iteration must use the supported JsonNode.properties() API");
    }

    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPomParent = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPomParent = current;
            }
            current = current.getParent();
        }
        if (lastPomParent != null) {
            return lastPomParent;
        }
        throw new IllegalStateException("Could not find project root");
    }
}
