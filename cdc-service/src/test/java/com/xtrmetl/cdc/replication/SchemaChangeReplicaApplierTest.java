package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
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
    void rewritesCreateIndexToIfNotExists() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "CREATE INDEX idx_processed_data_id ON processed_data (id)";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("CREATE INDEX IF NOT EXISTS idx_processed_data_id ON processed_data (id)"));
    }

    @Test
    void rewritesCreateSchemaToIfNotExists() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "CREATE SCHEMA reporting";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("CREATE SCHEMA IF NOT EXISTS reporting"));
    }

    @Test
    void blocksMultiStatementDdl() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "CREATE TABLE test(id int); DROP TABLE processed_data";
        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}")
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rewritesDropIndexToIfExists() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "DROP INDEX idx_processed_data_id";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("DROP INDEX IF EXISTS idx_processed_data_id"));
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
    void rewritesDropColumnToIfExists() {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "ALTER TABLE processed_data DROP COLUMN old_col";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("ALTER TABLE processed_data DROP COLUMN IF EXISTS old_col"));
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
    void allowsDdlWhenValidationModeIsWhitelist() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(
                jdbcTemplate,
                objectMapper,
                true,
                "whitelist",
                "CREATE TABLE,ALTER TABLE,CREATE INDEX,DROP INDEX",
                ""
        );

        String ddl = "CREATE TABLE test(id int)";
        applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}");

        verify(jdbcTemplate).execute(eq("CREATE TABLE IF NOT EXISTS test(id int)"));
    }

    @Test
    void blocksDdlWhenValidationModeIsWhitelist() {
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(
                jdbcTemplate,
                objectMapper,
                true,
                "whitelist",
                "CREATE TABLE,ALTER TABLE,CREATE INDEX,DROP INDEX",
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"DROP TABLE test\"}}")
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void requiresAllowedPrefixesWhenValidationModeIsWhitelist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeReplicaApplier(jdbcTemplate, objectMapper, true, "whitelist", "   ", "")
        );
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
    void ignoresDuplicateDdlErrors(CapturedOutput output) {
        SchemaChangeReplicaApplier applier = applier(true);

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
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("already applied"));
    }

    @Test
    void rethrowsAndLogsWhenJdbcExecutionFails(CapturedOutput output) {
        SchemaChangeReplicaApplier applier = applier(true);

        String ddl = "CREATE TABLE test(id int)";
        String rewritten = "CREATE TABLE IF NOT EXISTS test(id int)";
        DataAccessException failure = new DataAccessException("boom") {};
        doThrow(failure).when(jdbcTemplate).execute(eq(rewritten));

        DataAccessException thrown = assertThrows(
                DataAccessException.class,
                () -> applier.apply("xtrmetl-cdc.schema-changes", null, "{\"payload\":{\"ddl\":\"" + ddl + "\"}}")
        );
        assertSame(failure, thrown);
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("Failed to apply schema change DDL"));
    }
}
