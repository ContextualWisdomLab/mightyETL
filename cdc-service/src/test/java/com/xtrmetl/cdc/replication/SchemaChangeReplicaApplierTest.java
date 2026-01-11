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

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    private SchemaChangeReplicaApplier applier(boolean ddlEnabled) {
        return new SchemaChangeReplicaApplier(
                jdbcTemplate,
                objectMapper,
                ddlEnabled,
                "none",
                "",
                ""
        );
    }

    @Test
    void ignoresWhenDdlReplicationIsDisabled() {
        SchemaChangeReplicaApplier applier = applier(false);

        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"CREATE TABLE test(id int)\"}}");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresNonSchemaChangeTopics() {
        SchemaChangeReplicaApplier applier = applier(true);

        applier.apply("xtrmetl-cdc.public.processed_data", null, "{\"payload\":{\"ddl\":\"CREATE TABLE test(id int)\"}}");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresBlankValues() {
        SchemaChangeReplicaApplier applier = applier(true);

        applier.apply("xtrmetl-cdc.schema-changes", null, null);
        applier.apply("xtrmetl-cdc.schema-changes", null, "  ");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ignoresWhenDdlIsMissing() {
        SchemaChangeReplicaApplier applier = applier(true);

        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{}}");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void executesDdlWhenPresent() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "CREATE TABLE test(id int)";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("CREATE TABLE IF NOT EXISTS test(id int)"));
    }

    @Test
    void executesDdlFromRootWhenPayloadIsMissing() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "CREATE TABLE test(id int)";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"ddl\":\"" + ddl + "\"}");

        verify(jdbcTemplate).execute(eq("CREATE TABLE IF NOT EXISTS test(id int)"));
    }

    @Test
    void rewritesAllAddColumnClausesToIfNotExists() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "ALTER TABLE processed_data ADD COLUMN new_col INT, ADD COLUMN other_col TEXT";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq(
                "ALTER TABLE processed_data ADD COLUMN IF NOT EXISTS new_col INT, ADD COLUMN IF NOT EXISTS other_col TEXT"
        ));
    }

    @Test
    void doesNotDuplicateIfNotExistsOnAlterTableAddColumn() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "ALTER TABLE processed_data ADD COLUMN IF NOT EXISTS new_col INT";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("ALTER TABLE processed_data ADD COLUMN IF NOT EXISTS new_col INT"));
    }

    @Test
    void blocksDdlWhenValidationModeIsBlacklist() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(
                jdbcTemplate,
                objectMapper,
                true,
                "blacklist",
                "",
                "DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"DROP TABLE test\"}}")
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void executesDestructiveDdlWhenValidationModeIsNone() {
        SchemaChangeReplicaApplier applier = applier(true);

        String[] ddls = {
                "DROP TABLE processed_data",
                "TRUNCATE processed_data",
                "DROP SCHEMA public"
        };

        for (String ddl : ddls) {
            applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");
        }

        for (String ddl : ddls) {
            verify(jdbcTemplate).execute(eq(ddl));
        }
    }

    @Test
    void ignoresDuplicateDdlErrors() {
        SchemaChangeReplicaApplier applier = applier(true);
        Logger logger = (Logger) LoggerFactory.getLogger(SchemaChangeReplicaApplier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            String ddl = "CREATE TABLE test(id int)";
            String rewritten = "CREATE TABLE IF NOT EXISTS test(id int)";
            DataAccessException duplicate = new DataAccessException(
                    "relation already exists",
                    new SQLException("relation already exists", "42P07")
            ) {};
            doThrow(duplicate).when(jdbcTemplate).execute(eq(rewritten));

            assertDoesNotThrow(() ->
                    applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}")
            );

            verify(jdbcTemplate).execute(eq(rewritten));
            assertTrue(
                    appender.list.stream()
                            .anyMatch(event -> event.getLevel() == Level.INFO
                                    && event.getFormattedMessage().contains("already applied"))
            );
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void rethrowsAndLogsWhenJdbcExecutionFails() {
        SchemaChangeReplicaApplier applier = applier(true);
        Logger logger = (Logger) LoggerFactory.getLogger(SchemaChangeReplicaApplier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            String ddl = "CREATE TABLE test(id int)";
            String rewritten = "CREATE TABLE IF NOT EXISTS test(id int)";
            DataAccessException failure = new DataAccessException("boom") {};
            doThrow(failure).when(jdbcTemplate).execute(eq(rewritten));

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
