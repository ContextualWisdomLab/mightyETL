package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves that invalid monetary input is rejected rather than converted into a legitimate zero.
 */
class EtlServiceAmountIntegrityTest {

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
    @ValueSource(strings = {
            "",
            "not-a-number",
            "123456789012345678901234567890123456789",
            "0.0000000000000000001",
            "1E+19"
    })
    void rejectsInvalidOrUnsupportedAmountsBeforeJdbc(String amount) {
        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlService.processData(jsonRecord("record_alpha", amount))
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void invalidAmountMakesTheWholeBatchFailBeforeAnyJdbcWrite() {
        String payload = """
                [
                  {"id":"record_alpha","amount":"10.00"},
                  {"id":"record_beta","amount":"not-a-number"}
                ]
                """;

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlService.processData(payload)
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    private static String jsonRecord(String id, String amount) {
        return "[{\"id\":\"" + id + "\",\"amount\":\"" + amount + "\"}]";
    }
}
