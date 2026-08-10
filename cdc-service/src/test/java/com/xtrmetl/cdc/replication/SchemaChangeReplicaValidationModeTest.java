package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.cdc.config.XtrmetlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SchemaChangeReplicaValidationModeTest {

    @Test
    void rejectsBlankValidationModeInsteadOfSilentlyDisablingPolicy() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeReplicaApplier(
                        jdbcTemplate,
                        new ObjectMapper(),
                        true,
                        "   ",
                        "CREATE TABLE,ALTER TABLE,CREATE INDEX",
                        "DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE"
                )
        );
    }

    @Test
    void rejectsNullValidationModeInsteadOfSilentlyDisablingPolicy() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeReplicaApplier(
                        jdbcTemplate,
                        new ObjectMapper(),
                        true,
                        null,
                        "CREATE TABLE,ALTER TABLE,CREATE INDEX",
                        "DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE"
                )
        );
    }

    @Test
    void configurationPropertiesDefaultToWhitelist() {
        assertEquals("whitelist", new XtrmetlProperties().getReplica().getDdlValidationMode());
    }
}
