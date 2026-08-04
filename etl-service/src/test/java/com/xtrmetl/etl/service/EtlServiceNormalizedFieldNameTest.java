package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Defines the unambiguous normalized field-name contract for transformed records.
 */
class EtlServiceNormalizedFieldNameTest {

    @Test
    void rejectsCaseVariantFieldNamesBeforeJdbc() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlService service = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties()
        );
        String ambiguousPayload = """
                [{
                  "id":"record_alpha",
                  "name":"first",
                  "NAME":"second"
                }]
                """;

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.processData(ambiguousPayload)
        );

        assertTrue(exception.getMessage().contains("case normalization"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsIdentifierAliasesThatWouldProduceTwoIdFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlService service = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties()
        );
        String ambiguousPayload = """
                [{
                  "id":"record_alpha",
                  "ID":"record_beta"
                }]
                """;

        assertThrows(RuntimeException.class, () -> service.processData(ambiguousPayload));
        verifyNoInteractions(jdbcTemplate);
    }
}
