package com.xtrmetl.cdc.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fail-first regression for optional malformed Debezium key metadata. */
class DebeziumMalformedKeyFallbackTest {

    @Test
    void malformedOptionalKeyFallsBackToValidAfterIdentifier() {
        DebeziumChangeRecordMapper mapper = new DebeziumChangeRecordMapper(new ObjectMapper());
        String value = """
                {"payload":{"op":"u","before":{"id":41,"data":"old"},
                "after":{"id":42,"data":"new"},
                "source":{"schema":"public","table":"processed_data"}}}
                """;

        Optional<CanonicalChangeRecord> result = mapper.map(
                "postgres-debezium",
                "xtrmetl-cdc.public.processed_data",
                "{malformed-key-json",
                value
        );

        assertTrue(result.isPresent(), "malformed optional key metadata must not discard a valid value envelope");
        CanonicalChangeRecord record = result.orElseThrow();
        assertEquals(42, ((Number) record.getPk().get("id")).intValue());
        assertEquals("new", record.getAfter().get("data"));
    }
}
