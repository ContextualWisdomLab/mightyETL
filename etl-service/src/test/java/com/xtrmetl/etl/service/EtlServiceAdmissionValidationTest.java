package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves durable job admission applies the synchronous request contract without database work.
 */
class EtlServiceAdmissionValidationTest {

    @Test
    void acceptsAValidBatchWithoutRequestLockOrJdbcWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());

        assertDoesNotThrow(() -> service.validateData(
                "[{\"id\":\"record_alpha\",\"amount\":\"1.25\"}]"
        ));

        verifyNoInteractions(jdbcTemplate, requestLock);
    }

    @Test
    void rejectsMalformedJsonWithoutDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.validateData("not-json")
        );

        assertEquals(EtlRequestError.INVALID_JSON, exception.error());
        verifyNoInteractions(jdbcTemplate, requestLock);
    }

    @Test
    void rejectsNormalizedDuplicateFieldsWithoutDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlService service = service(jdbcTemplate, requestLock, new EtlBatchProperties());

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.validateData(
                        "[{\"id\":\"record_alpha\",\"name\":\"one\",\"NAME\":\"two\"}]"
                )
        );

        assertEquals(EtlRequestError.INVALID_RECORD, exception.error());
        verifyNoInteractions(jdbcTemplate, requestLock);
    }

    @Test
    void rejectsOversizedPayloadWithoutDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(8);
        EtlService service = service(jdbcTemplate, requestLock, properties);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.validateData("[{\"id\":\"record_alpha\"}]")
        );

        assertEquals(EtlRequestError.PAYLOAD_TOO_LARGE, exception.error());
        verifyNoInteractions(jdbcTemplate, requestLock);
    }

    @Test
    void rejectsExcessRecordsWithoutDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxBatchRecords(1);
        EtlService service = service(jdbcTemplate, requestLock, properties);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.validateData(
                        "[{\"id\":\"record_alpha\"},{\"id\":\"record_beta\"}]"
                )
        );

        assertEquals(EtlRequestError.BATCH_TOO_LARGE, exception.error());
        verifyNoInteractions(jdbcTemplate, requestLock);
    }

    private static EtlService service(
            JdbcTemplate jdbcTemplate,
            EtlRequestLock requestLock,
            EtlBatchProperties properties
    ) {
        return new EtlService(jdbcTemplate, new ObjectMapper(), properties, requestLock);
    }
}
