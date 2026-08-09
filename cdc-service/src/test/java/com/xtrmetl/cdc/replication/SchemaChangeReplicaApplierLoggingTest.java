package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that schema-replication observability never republishes raw DDL contents.
 */
@ExtendWith(OutputCaptureExtension.class)
class SchemaChangeReplicaApplierLoggingTest {

    @Test
    void successfulApplyLogsOnlyBoundedMetadataNotRawDdl(CapturedOutput output) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SchemaChangeReplicaApplier applier = new SchemaChangeReplicaApplier(
                jdbcTemplate,
                new ObjectMapper(),
                true,
                "none",
                "",
                ""
        );
        String secretLiteral = "buyer-contract-secret-8472";
        String inputDdl = "CREATE TABLE confidential_record(secret_value text DEFAULT '" + secretLiteral + "')";
        String executedDdl = "CREATE TABLE IF NOT EXISTS confidential_record(secret_value text DEFAULT '"
                + secretLiteral + "')";

        applier.apply(
                "xtrmetl-cdc.schema-changes",
                null,
                "{\"payload\":{\"ddl\":\"" + inputDdl.replace("'", "\\u0027") + "\"}}"
        );

        verify(jdbcTemplate).execute(eq(executedDdl));
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("Applied schema change DDL on replica"));
        assertFalse(logs.contains(secretLiteral));
        assertFalse(logs.contains("DEFAULT"));
        assertFalse(logs.contains("ddl="));
    }
}
