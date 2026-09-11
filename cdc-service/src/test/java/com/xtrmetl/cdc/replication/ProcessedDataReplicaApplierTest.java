package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessedDataReplicaApplierTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private ProcessedDataReplicaApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ProcessedDataReplicaApplier(
                jdbcTemplate,
                new ObjectMapper(),
                java.util.Set.of("processed_data")
        );
    }

    @Test
    void appliesConfiguredExtraTableWithSameRowShape() {
        ProcessedDataReplicaApplier multi = new ProcessedDataReplicaApplier(
                jdbcTemplate,
                new ObjectMapper(),
                java.util.Set.of("processed_data", "audit_data")
        );
        String topic = "xtrmetl-cdc.public.audit_data";
        String keyJson = "{\"payload\":{\"id\":9}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":9,\"data\":\"x\"}}}";

        multi.apply(topic, keyJson, valueJson);

        verify(jdbcTemplate).update(startsWith("INSERT INTO audit_data"), eq(9L), eq("x"));
    }

    @Test
    void parseTablesRejectsUnsafeIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> ProcessedDataReplicaApplier.parseTables("processed_data;drop"));
    }

    @Test
    void constructorRejectsUnsafeIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessedDataReplicaApplier(
                        jdbcTemplate,
                        new ObjectMapper(),
                        java.util.Set.of("processed_data;drop")
                )
        );
    }

    @Test
    void ignoresInjectedTopicSuffix() {
        String topic = "xtrmetl-cdc.public.processed_data;drop_table";
        String keyJson = "{\"payload\":{\"id\":1}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresTopicWithoutDelimiter() {
        assertDoesNotThrow(() -> applier.apply("processed_data", null, null));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresTopicWithEmptySuffix() {
        assertDoesNotThrow(() -> applier.apply("xtrmetl-cdc.public.", null, null));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresNonProcessedDataTopics() {
        applier.apply("xtrmetl-cdc.public.users", "{\"payload\":{\"id\":1}}", "{\"payload\":{\"op\":\"c\"}}");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresTombstoneValue() {
        applier.apply("xtrmetl-cdc.public.processed_data", "{\"payload\":{\"id\":1}}", null);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresMissingPayload() {
        applier.apply("xtrmetl-cdc.public.processed_data", "{\"payload\":{\"id\":1}}", "{\"not_payload\":{\"op\":\"c\"}}");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void throwsWhenDataIsNull() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":1}}";
        String valueJson = "{\"payload\":{\"op\":\"u\",\"after\":{\"id\":1,\"data\":null}}}";

        assertThrows(IllegalStateException.class, () -> applier.apply(topic, keyJson, valueJson));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void upsertsOnCreateEvent() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":1}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verify(jdbcTemplate).update(startsWith("INSERT INTO processed_data"), eq(1L), eq("hello"));
        verify(jdbcTemplate, never()).update(startsWith("DELETE FROM processed_data"), eq(1L));
    }

    @Test
    void upsertsOnEmptyStringData() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":1}}";
        String valueJson = "{\"payload\":{\"op\":\"u\",\"after\":{\"id\":1,\"data\":\"\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verify(jdbcTemplate).update(startsWith("INSERT INTO processed_data"), eq(1L), eq(""));
    }

    @Test
    void deletesOnDeleteEvent() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":1}}";
        String valueJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"data\":\"hello\"},\"after\":null}}";

        applier.apply(topic, keyJson, valueJson);

        verify(jdbcTemplate).update(eq("DELETE FROM processed_data WHERE id = ?"), eq(1L));
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO processed_data"), eq(1L), eq("hello"));
    }

    @Test
    void doesNotCoerceFractionalNumericKeyIdIntoAnotherRow() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":1.5}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1.5,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void doesNotCoerceFractionalNumericValueIdIntoAnotherRow() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = null;
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1.5,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void doesNotAcceptFloatingPointTypedIntegralId() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":1.0}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1.0,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void doesNotWrapNumericIdAboveLongMax() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":9223372036854775808}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":9223372036854775808,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void doesNotWrapNumericIdBelowLongMin() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String keyJson = "{\"payload\":{\"id\":-9223372036854775809}}";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":-9223372036854775809,\"data\":\"hello\"}}}";

        applier.apply(topic, keyJson, valueJson);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void keepsExactSignedLongBoundaryIds() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String maxValue = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":9223372036854775807,\"data\":\"hello\"}}}";
        String minValue = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":-9223372036854775808,\"data\":\"hello\"}}}";

        applier.apply(topic, "{\"payload\":{\"id\":9223372036854775807}}", maxValue);
        applier.apply(topic, "{\"payload\":{\"id\":-9223372036854775808}}", minValue);

        verify(jdbcTemplate).update(startsWith("INSERT INTO processed_data"), eq(Long.MAX_VALUE), eq("hello"));
        verify(jdbcTemplate).update(startsWith("INSERT INTO processed_data"), eq(Long.MIN_VALUE), eq("hello"));
    }

    @Test
    void keepsOrdinaryIntegralNumericAndStrictTextIds() {
        String topic = "xtrmetl-cdc.public.processed_data";
        String valueJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":42,\"data\":\"hello\"}}}";

        applier.apply(topic, "{\"payload\":{\"id\":42}}", valueJson);
        applier.apply(topic, "{\"payload\":{\"id\":\"42\"}}", valueJson);

        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(startsWith("INSERT INTO processed_data"), eq(42L), eq("hello"));
    }
}
