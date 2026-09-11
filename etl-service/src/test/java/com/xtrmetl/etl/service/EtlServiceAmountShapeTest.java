package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Verifies the JSON shape contract for monetary input at the real ETL service boundary. */
class EtlServiceAmountShapeTest {

    private JdbcTemplate jdbcTemplate;
    private EtlService etlService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(65_536);
        properties.setMaxBatchRecords(100);
        etlService = new EtlService(jdbcTemplate, new ObjectMapper(), properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "true"})
    void rejectsAmountValuesThatAreNotStringOrNumberScalars(String amountJson) {
        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlService.processData(
                        "[{\"id\":\"record_alpha\",\"amount\":" + amountJson + "}]"
                )
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void acceptsNumericAmountScalarWithoutChangingDecimalFormatting() {
        etlService.processData("[{\"id\":\"record_alpha\",\"amount\":100.5}]");

        verify(jdbcTemplate).update(anyString(), contains("AMOUNT:100.50"));
    }
}
