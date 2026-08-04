package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines the stable machine-readable contract for deterministic ETL request failures.
 */
class EtlRequestExceptionTest {

    @ParameterizedTest
    @MethodSource("errorContracts")
    void exposesStableProblemMetadata(
            EtlRequestError error,
            HttpStatus status,
            String errorCode,
            URI type,
            String title,
            String detail
    ) {
        assertEquals(status, error.status());
        assertEquals(errorCode, error.errorCode());
        assertEquals(type, error.type());
        assertEquals(title, error.title());
        assertEquals(detail, error.detail());
    }

    @Test
    void retainsClassificationAndOptionalCause() {
        IllegalArgumentException cause = new IllegalArgumentException("synthetic-secret");
        EtlRequestException exception = new EtlRequestException(
                EtlRequestError.INVALID_RECORD,
                cause
        );

        assertSame(EtlRequestError.INVALID_RECORD, exception.error());
        assertSame(cause, exception.getCause());
        assertEquals("etl_invalid_record", exception.getMessage());
    }

    @Test
    void supportsCauseFreeConstruction() {
        EtlRequestException exception = new EtlRequestException(
                EtlRequestError.INVALID_JSON
        );

        assertSame(EtlRequestError.INVALID_JSON, exception.error());
        assertNull(exception.getCause());
    }

    @Test
    void rejectsNullClassification() {
        assertThrows(NullPointerException.class, () -> new EtlRequestException(null));
        assertThrows(
                NullPointerException.class,
                () -> new EtlRequestException(null, new IllegalStateException("cause"))
        );
    }

    private static Stream<Arguments> errorContracts() {
        return Stream.of(
                Arguments.of(
                        EtlRequestError.PAYLOAD_TOO_LARGE,
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "etl_payload_too_large",
                        URI.create("urn:mightyetl:problem:etl-payload-too-large"),
                        "ETL payload too large",
                        "The ETL request payload exceeds the configured limit."
                ),
                Arguments.of(
                        EtlRequestError.BATCH_TOO_LARGE,
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "etl_batch_too_large",
                        URI.create("urn:mightyetl:problem:etl-batch-too-large"),
                        "ETL batch too large",
                        "The ETL request contains too many records."
                ),
                Arguments.of(
                        EtlRequestError.INVALID_JSON,
                        HttpStatus.BAD_REQUEST,
                        "etl_invalid_json",
                        URI.create("urn:mightyetl:problem:etl-invalid-json"),
                        "Invalid ETL JSON",
                        "The request body must be a valid JSON array."
                ),
                Arguments.of(
                        EtlRequestError.INVALID_RECORD,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "etl_invalid_record",
                        URI.create("urn:mightyetl:problem:etl-invalid-record"),
                        "Invalid ETL record",
                        "One or more ETL records violate the request contract."
                )
        );
    }
}
