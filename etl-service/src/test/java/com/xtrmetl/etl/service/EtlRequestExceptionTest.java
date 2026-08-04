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
                ),
                Arguments.of(
                        EtlRequestError.INVALID_IDEMPOTENCY_KEY,
                        HttpStatus.BAD_REQUEST,
                        "etl_invalid_idempotency_key",
                        URI.create("urn:mightyetl:problem:etl-invalid-idempotency-key"),
                        "Invalid ETL idempotency key",
                        "Idempotency-Key must be a quoted Structured Field String or a supported legacy raw value containing 16 to 128 safe ASCII characters."
                ),
                Arguments.of(
                        EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                        HttpStatus.UNAUTHORIZED,
                        "etl_idempotency_principal_required",
                        URI.create("urn:mightyetl:problem:etl-idempotency-principal-required"),
                        "ETL idempotency authentication required",
                        "An authenticated principal is required when Idempotency-Key is supplied."
                ),
                Arguments.of(
                        EtlRequestError.JOB_PRINCIPAL_REQUIRED,
                        HttpStatus.UNAUTHORIZED,
                        "etl_job_principal_required",
                        URI.create("urn:mightyetl:problem:etl-job-principal-required"),
                        "ETL job authentication required",
                        "An authenticated principal is required for ETL job resources."
                ),
                Arguments.of(
                        EtlRequestError.JOB_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "etl_job_not_found",
                        URI.create("urn:mightyetl:problem:etl-job-not-found"),
                        "ETL job not found",
                        "The requested ETL job was not found."
                ),
                Arguments.of(
                        EtlRequestError.IDEMPOTENCY_KEY_REUSED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "etl_idempotency_key_reused",
                        URI.create("urn:mightyetl:problem:etl-idempotency-key-reused"),
                        "ETL idempotency key reused",
                        "The Idempotency-Key was already used with a different request payload."
                ),
                Arguments.of(
                        EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                        HttpStatus.CONFLICT,
                        "etl_idempotency_request_in_progress",
                        URI.create("urn:mightyetl:problem:etl-idempotency-request-in-progress"),
                        "ETL idempotency request in progress",
                        "A request with the same principal-scoped Idempotency-Key is still being processed."
                )
        );
    }
}
