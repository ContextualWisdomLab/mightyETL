package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that schema-replication observability never republishes raw DDL or driver diagnostics.
 */
@ExtendWith(OutputCaptureExtension.class)
class SchemaChangeReplicaApplierLoggingTest {

    @Test
    void successfulApplyLogsOnlyBoundedMetadataNotRawDdl(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String secretLiteral = "buyer-contract-secret-8472";
        String inputDdl = "CREATE TABLE confidential_record(secret_value text DEFAULT '" + secretLiteral + "')";
        String executedDdl = "CREATE TABLE IF NOT EXISTS confidential_record(secret_value text DEFAULT '"
                + secretLiteral + "')";

        applier.apply(schemaTopic(), null, ddlEnvelope(inputDdl));

        verify(jdbcTemplate).execute(eq(executedDdl));
        assertSafeLogs(output, "Applied schema change DDL on replica", secretLiteral, "DEFAULT", "ddl=");
    }

    @Test
    void blockedDdlLogsPolicyOutcomeWithoutRawStatement(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(
                jdbcTemplate,
                "whitelist",
                "CREATE TABLE,ALTER TABLE,CREATE INDEX",
                ""
        );
        String secretPath = "/srv/private/buyer-contract-secret-8472";
        String ddl = "CREATE TABLESPACE reporting LOCATION '" + secretPath + "'";

        assertThrows(IllegalArgumentException.class, () -> applier.apply(schemaTopic(), null, ddlEnvelope(ddl)));

        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(output, "Blocked DDL by validation policy", secretPath, "TABLESPACE", "ddl=");
    }

    @Test
    void multiStatementBlockLogsClassificationWithoutRawStatement(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String secretLiteral = "buyer-contract-secret-8472";
        String ddl = "CREATE TABLE confidential_record(id int); DROP TABLE " + secretLiteral;

        assertThrows(IllegalArgumentException.class, () -> applier.apply(schemaTopic(), null, ddlEnvelope(ddl)));

        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(output, "Blocked multi-statement DDL", secretLiteral, "DROP TABLE", "ddl=");
    }

    @Test
    void sqlCommentBlockLogsClassificationWithoutRawStatement(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String secretLiteral = "buyer-contract-secret-8472";
        String ddl = "CREATE TABLE confidential_record(id int) -- " + secretLiteral;

        assertThrows(IllegalArgumentException.class, () -> applier.apply(schemaTopic(), null, ddlEnvelope(ddl)));

        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(
                output,
                "Blocked DDL containing SQL comments or NUL",
                secretLiteral,
                "confidential_record",
                "ddl="
        );
    }

    @Test
    void nulBlockLogsClassificationWithoutRawStatement(CapturedOutput output) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String secretLiteral = "buyer-contract-secret-8472";
        String ddl = "CREATE TABLE confidential_record(id int) " + (char) 0 + secretLiteral;
        String envelope = new ObjectMapper().writeValueAsString(Map.of("payload", Map.of("ddl", ddl)));

        assertThrows(IllegalArgumentException.class, () -> applier.apply(schemaTopic(), null, envelope));

        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(
                output,
                "Blocked DDL containing SQL comments or NUL",
                secretLiteral,
                "confidential_record",
                "ddl="
        );
    }

    @Test
    void duplicateDdlLogsOutcomeWithoutSqlOrDriverDiagnostics(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String secretLiteral = "buyer-contract-secret-8472";
        String inputDdl = "CREATE TABLE confidential_record(secret_value text DEFAULT '" + secretLiteral + "')";
        String executedDdl = "CREATE TABLE IF NOT EXISTS confidential_record(secret_value text DEFAULT '"
                + secretLiteral + "')";
        SQLException sqlException = new SQLException(
                "relation already exists at jdbc:postgresql://db.internal/prod?password=driver-secret",
                "42P07"
        );
        DataAccessException duplicate = new DataAccessException("driver-secret", sqlException) {};
        doThrow(duplicate).when(jdbcTemplate).execute(eq(executedDdl));

        assertDoesNotThrow(() -> applier.apply(schemaTopic(), null, ddlEnvelope(inputDdl)));

        verify(jdbcTemplate).execute(eq(executedDdl));
        assertSafeLogs(
                output,
                "Schema change DDL already applied; skipping duplicate",
                secretLiteral,
                "driver-secret",
                "jdbc:postgresql://",
                "ddl="
        );
    }

    @Test
    void executionFailureLogsOutcomeWithoutSqlOrDriverDiagnostics(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String secretLiteral = "buyer-contract-secret-8472";
        String inputDdl = "CREATE TABLE confidential_record(secret_value text DEFAULT '" + secretLiteral + "')";
        String executedDdl = "CREATE TABLE IF NOT EXISTS confidential_record(secret_value text DEFAULT '"
                + secretLiteral + "')";
        DataAccessException failure = new DataAccessException(
                "jdbc:postgresql://db.internal/prod?password=driver-secret"
        ) {};
        doThrow(failure).when(jdbcTemplate).execute(eq(executedDdl));

        DataAccessException thrown = assertThrows(
                DataAccessException.class,
                () -> applier.apply(schemaTopic(), null, ddlEnvelope(inputDdl))
        );

        assertSame(failure, thrown);
        assertSafeLogs(
                output,
                "Failed to apply schema change DDL on replica",
                secretLiteral,
                "driver-secret",
                "jdbc:postgresql://",
                "ddl="
        );
    }

    @Test
    void malformedEventLogsOnlyStableParseClassification(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = applier(jdbcTemplate, "none", "", "");
        String malformed = "{buyer-contract-secret-8472";

        assertDoesNotThrow(() -> applier.apply(schemaTopic(), null, malformed));

        verifyNoInteractions(jdbcTemplate);
        assertSafeLogs(
                output,
                "Failed to parse Debezium schema change JSON; skipping DDL apply",
                "buyer-contract-secret-8472",
                "JsonParseException",
                "ReaderBasedJsonParser"
        );
    }

    private static SchemaChangeReplicaApplier applier(
            JdbcTemplate jdbcTemplate,
            String validationMode,
            String allowedPrefixes,
            String blockedPrefixes
    ) {
        return new SchemaChangeReplicaApplier(
                jdbcTemplate,
                new ObjectMapper(),
                true,
                validationMode,
                allowedPrefixes,
                blockedPrefixes
        );
    }

    private static String schemaTopic() {
        return "xtrmetl-cdc.schema-changes";
    }

    private static String ddlEnvelope(String ddl) {
        return "{\"payload\":{\"ddl\":\"" + ddl.replace("'", "\\u0027") + "\"}}";
    }

    private static void assertSafeLogs(CapturedOutput output, String expected, String... forbidden) {
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains(expected));
        for (String value : forbidden) {
            assertFalse(logs.contains(value), () -> "Log output exposed forbidden value: " + value);
        }
    }
}
