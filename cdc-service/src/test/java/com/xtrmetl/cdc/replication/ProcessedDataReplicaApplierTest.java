package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class ProcessedDataReplicaApplierTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProcessedDataReplicaApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ProcessedDataReplicaApplier(jdbcTemplate, new ObjectMapper());
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
}
