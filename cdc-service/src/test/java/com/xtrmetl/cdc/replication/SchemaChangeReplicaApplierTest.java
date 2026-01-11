package com.xtrmetl.cdc.replication;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void executesDdlFromRootWhenPayloadIsMissing() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true);

        String ddl = "CREATE TABLE test(id int)";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"ddl\":\"" + ddl + "\"}");

        verify(jdbcTemplate).execute(eq(ddl));
    }

    @Test
    void rethrowsAndLogsWhenJdbcExecutionFails() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true);
        Logger logger = (Logger) LoggerFactory.getLogger(SchemaChangeReplicaApplier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            String ddl = "CREATE TABLE test(id int)";
            DataAccessException failure = new DataAccessException("boom") {};
            doThrow(failure).when(jdbcTemplate).execute(eq(ddl));

            DataAccessException thrown = assertThrows(
                    DataAccessException.class,
                    () -> applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}")
            );
            assertSame(failure, thrown);
            assertTrue(
                    appender.list.stream()
                            .anyMatch(event -> event.getLevel() == Level.ERROR
                                    && event.getFormattedMessage().contains("Failed to apply schema change DDL"))
            );
        } finally {
            logger.detachAppender(appender);
        }
    }
}
