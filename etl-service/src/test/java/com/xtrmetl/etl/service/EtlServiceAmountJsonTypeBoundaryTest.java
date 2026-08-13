package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Exercises the JSON type boundary for monetary input at the real ETL transformation path.
 */
class EtlServiceAmountJsonTypeBoundaryTest {

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
    @ValueSource(strings = {"null", "{}", "[]", "true"})
    void rejectsNonNumericJsonAmountsBeforeJdbc(String rawAmount) {
        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlService.processData(recordWithRawAmount(rawAmount))
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "\"0\""})
    void acceptsRealZeroAsNumberOrNumericString(String rawAmount) {
        etlService.processData(recordWithRawAmount(rawAmount));

        verify(jdbcTemplate).update(anyString(), eq("ID:record_alpha,AMOUNT:0.00,"));
    }

    private static String recordWithRawAmount(String rawAmount) {
        return "[{\"id\":\"record_alpha\",\"amount\":" + rawAmount + "}]";
    }
}
