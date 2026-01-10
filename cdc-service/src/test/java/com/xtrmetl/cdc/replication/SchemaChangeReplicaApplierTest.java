package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SchemaChangeReplicaApplierTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void ignoresWhenDdlReplicationIsDisabled() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, false);

        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"CREATE TABLE test(id int)\"}}");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresNonSchemaChangeTopics() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true);

        applier.apply("xtrmetl-cdc.public.processed_data", null, "{\"payload\":{\"ddl\":\"CREATE TABLE test(id int)\"}}");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresBlankValues() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true);

        applier.apply("xtrmetl-cdc.schema-changes", null, null);
        applier.apply("xtrmetl-cdc.schema-changes", null, "  ");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresWhenDdlIsMissing() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true);

        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{}}");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void executesDdlWhenPresent() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true);

        String ddl = "CREATE TABLE test(id int)";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq(ddl));
    }
}

