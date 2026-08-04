package com.xtrmetl.etl.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves that ETL-specific parser hardening does not change shared application JSON behavior.
 */
class EtlServiceObjectMapperIsolationTest {

    @Test
    void leavesCallerOwnedObjectMapperConfigurationUnchanged() {
        ObjectMapper sharedMapper = new ObjectMapper();
        assertFalse(sharedMapper.isEnabled(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

        new EtlService(mock(JdbcTemplate.class), sharedMapper, new EtlBatchProperties());

        assertFalse(sharedMapper.isEnabled(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    }

    @Test
    void rejectsDuplicateFieldsWithTheIsolatedParser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlService service = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties()
        );
        String duplicateFieldPayload = """
                [{
                  "id":"record_alpha",
                  "name":"first",
                  "name":"second"
                }]
                """;

        assertThrows(RuntimeException.class, () -> service.processData(duplicateFieldPayload));
        verifyNoInteractions(jdbcTemplate);
    }
}
