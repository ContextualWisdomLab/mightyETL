package com.xtrmetl.cdc.replication;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that row-replication observability keeps business identifiers and parser diagnostics out of logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProcessedDataReplicaApplierLoggingTest {

    @Test
    void missingDataFailureDoesNotExposeRowIdentifier(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProcessedDataReplicaApplier applier = applier(jdbcTemplate);
        long sensitiveRowId = 918273645L;
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":" + sensitiveRowId + "}}";
        String valueJson = "{\"payload\":{\"op\":\"u\",\"after\":{\"id\":" + sensitiveRowId + "}}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> applier.apply(topic, keyJson, valueJson)
        );

        assertEquals("Missing data field in CDC event", failure.getMessage());
        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(output, "Replica apply failed: missing data field", Long.toString(sensitiveRowId));
    }

    @Test
    void nullDataFailureDoesNotExposeRowIdentifier(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProcessedDataReplicaApplier applier = applier(jdbcTemplate);
        long sensitiveRowId = 817263544L;
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":" + sensitiveRowId + "}}";
        String valueJson = "{\"payload\":{\"op\":\"u\",\"after\":{\"id\":" + sensitiveRowId + ",\"data\":null}}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> applier.apply(topic, keyJson, valueJson)
        );

        assertEquals("CDC event data is null", failure.getMessage());
        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(output, "Replica apply failed: data is null", Long.toString(sensitiveRowId));
    }

    @Test
    void malformedValueLogsOnlyStableClassification(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProcessedDataReplicaApplier applier = applier(jdbcTemplate);
        String malformedValue = "{buyer-contract-secret-8472";

        applier.apply("xtrmetl-cdc.public.processed_data", null, malformedValue);

        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(
                output,
                "Failed to parse Debezium value JSON; skipping replica apply",
                "buyer-contract-secret-8472",
                "JsonParseException",
                "ReaderBasedJsonParser"
        );
    }

    @Test
    void malformedKeyFallbackLogsOnlyStableClassification(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ProcessedDataReplicaApplier applier = applier(jdbcTemplate);
        String malformedKey = "{buyer-key-secret-8472";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":7,\"data\":\"accepted\"}}}";
        Logger logger = (Logger) LoggerFactory.getLogger(ProcessedDataReplicaApplier.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            applier.apply("xtrmetl-cdc.public.processed_data", malformedKey, valueJson);
        } finally {
            logger.setLevel(previousLevel);
        }

        verify(jdbcTemplate).update(startsWith("INSERT INTO processed_data"), eq(7L), eq("accepted"));
        assertSafeLogs(
                output,
                "Failed to parse Debezium key JSON; falling back to value payload",
                "buyer-key-secret-8472",
                "JsonParseException",
                "ReaderBasedJsonParser"
        );
    }

    private static ProcessedDataReplicaApplier applier(JdbcTemplate jdbcTemplate) {
        return new ProcessedDataReplicaApplier(
                jdbcTemplate,
                new ObjectMapper(),
                Set.of("processed_data")
        );
    }

    private static void assertSafeLogs(CapturedOutput output, String expected, String... forbidden) {
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains(expected));
        for (String value : forbidden) {
            assertFalse(logs.contains(value), () -> "Log output exposed forbidden value: " + value);
        }
    }
}
