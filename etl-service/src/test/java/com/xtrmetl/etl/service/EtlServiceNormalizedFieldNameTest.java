package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Defines the unambiguous normalized field-name contract for transformed records.
 */
class EtlServiceNormalizedFieldNameTest {

    @Test
    void rejectsCaseVariantFieldNamesBeforeJdbc() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlService service = service(jdbcTemplate);
        String ambiguousPayload = """
                [{
                  "id":"record_alpha",
                  "name":"first",
                  "NAME":"second"
                }]
                """;

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.processData(ambiguousPayload)
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsIdentifierAliasesThatWouldProduceTwoIdFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlService service = service(jdbcTemplate);
        String ambiguousPayload = """
                [{
                  "id":"record_alpha",
                  "ID":"record_beta"
                }]
                """;

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.processData(ambiguousPayload)
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    private static EtlService service(JdbcTemplate jdbcTemplate) {
        return new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties()
        );
    }
}
