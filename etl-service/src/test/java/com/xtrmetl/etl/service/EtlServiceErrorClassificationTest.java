package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves that deterministic ETL request failures receive stable typed classifications.
 */
class EtlServiceErrorClassificationTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void classifiesUtf8PayloadLimitFailure() {
        EtlBatchProperties properties = properties(24, 10);
        String payload = "[{\"id\":\"record_alpha\",\"name\":\"한글데이터\"}]";

        assertError(EtlRequestError.PAYLOAD_TOO_LARGE, payload, properties);
    }

    @Test
    void classifiesRecordCountLimitFailure() {
        EtlBatchProperties properties = properties(4096, 1);
        String payload = "[{\"id\":\"record_alpha\"},{\"id\":\"record_beta\"}]";

        assertError(EtlRequestError.BATCH_TOO_LARGE, payload, properties);
    }

    @Test
    void classifiesNullPayloadAsInvalidJson() {
        assertError(EtlRequestError.INVALID_JSON, null, properties(4096, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "null",
            "{\"id\":\"record_alpha\"}"
    })
    void classifiesMalformedOrNonArrayPayloadAsInvalidJson(String payload) {
        assertError(EtlRequestError.INVALID_JSON, payload, properties(4096, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[\"not-an-object\"]",
            "[{\"name\":\"missing-id\"}]",
            "[{\"id\":123}]",
            "[{\"id\":\" record_alpha \\"}]",
            "[{\"id\":\"record_alpha\",\"Name\":\"first\",\"NAME\":\"second\"}]"
    })
    void classifiesSemanticRecordFailuresAsInvalidRecord(String payload) {
        assertError(EtlRequestError.INVALID_RECORD, payload, properties(4096, 10));
    }

    @Test
    void classifiesDuplicateJsonFieldsAsInvalidRecord() {
        String payload = """
                [{
                  "id":"record_alpha",
                  "name":"first",
                  "name":"second"
                }]
                """;

        assertError(EtlRequestError.INVALID_RECORD, payload, properties(4096, 10));
    }

    private void assertError(
            EtlRequestError expected,
            String payload,
            EtlBatchProperties properties
    ) {
        EtlService service = new EtlService(jdbcTemplate, objectMapper, properties);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.processData(payload)
        );

        assertSame(expected, exception.error());
        verifyNoInteractions(jdbcTemplate);
    }

    private static EtlBatchProperties properties(int maxPayloadBytes, int maxBatchRecords) {
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(maxPayloadBytes);
        properties.setMaxBatchRecords(maxBatchRecords);
        return properties;
    }
}
