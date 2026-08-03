package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SchemaChangeReplicaApplierSecurityTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private SchemaChangeReplicaApplier applier;

    @BeforeEach
    void setUp() {
        applier = new SchemaChangeReplicaApplier(
                jdbcTemplate,
                new ObjectMapper(),
                true,
                "whitelist",
                "CREATE TABLE,ALTER TABLE,CREATE INDEX",
                "DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE"
        );
    }

    @Test
    void blocksPrefixConfusionThatOnlyStartsWithAllowedCommand() {
        String ddl = "CREATE TABLESPACE reporting LOCATION '/tmp'";

        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply(schemaTopic(), null, schemaEvent(ddl))
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void blocksSqlCommentsBeforeExecution() {
        String ddl = "CREATE TABLE test(id int) -- trailing comment";

        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply(schemaTopic(), null, schemaEvent(ddl))
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void allowsAConfiguredSingleStatement() {
        applier.apply(schemaTopic(), null, schemaEvent("CREATE TABLE test(id int)"));

        verify(jdbcTemplate).execute(eq("CREATE TABLE IF NOT EXISTS test(id int)"));
    }

    private static String schemaTopic() {
        return "xtrmetl-cdc.schema-changes";
    }

    private static String schemaEvent(String ddl) {
        return "{\"payload\":{\"ddl\":\"" + ddl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}}";
    }
}
