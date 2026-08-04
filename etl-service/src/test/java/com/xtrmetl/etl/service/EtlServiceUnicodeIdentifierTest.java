package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that visually blank Unicode separators cannot disguise record-identifier boundaries.
 */
class EtlServiceUnicodeIdentifierTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EtlService etlService = new EtlService(
            jdbcTemplate,
            new ObjectMapper(),
            new EtlBatchProperties()
    );

    @Test
    void rejectsUnicodeSpaceSeparatorsAtEitherIdentifierEdgeBeforeJdbc() {
        assertThrows(
                RuntimeException.class,
                () -> etlService.processData("[{\"id\":\"\\u00A0record_alpha\"}]")
        );
        assertThrows(
                RuntimeException.class,
                () -> etlService.processData("[{\"id\":\"record_alpha\\u202F\"}]")
        );

        verifyNoInteractions(jdbcTemplate);
    }
}
