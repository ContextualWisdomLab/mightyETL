package com.xtrmetl.etl.service;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Objects;

/**
 * Stable client-visible classifications for deterministic ETL request rejection.
 *
 * <p>Each enum value owns the complete RFC 9457 metadata that may be returned to callers. The
 * values contain fixed text only; exception messages and causes are intentionally excluded from
 * this contract.</p>
 */
public enum EtlRequestError {

    /** The UTF-8 request body exceeds the configured byte limit. */
    PAYLOAD_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "etl_payload_too_large",
            "urn:mightyetl:problem:etl-payload-too-large",
            "ETL payload too large",
            "The ETL request payload exceeds the configured limit."
    ),

    /** The JSON array contains more records than the configured batch limit. */
    BATCH_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "etl_batch_too_large",
            "urn:mightyetl:problem:etl-batch-too-large",
            "ETL batch too large",
            "The ETL request contains too many records."
    ),

    /** The request body is absent, malformed, or not a top-level JSON array. */
    INVALID_JSON(
            HttpStatus.BAD_REQUEST,
            "etl_invalid_json",
            "urn:mightyetl:problem:etl-invalid-json",
            "Invalid ETL JSON",
            "The request body must be a valid JSON array."
    ),

    /** At least one record violates the deterministic ETL request contract. */
    INVALID_RECORD(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "etl_invalid_record",
            "urn:mightyetl:problem:etl-invalid-record",
            "Invalid ETL record",
            "One or more ETL records violate the request contract."
    );

    private final HttpStatus status;
    private final String errorCode;
    private final URI type;
    private final String title;
    private final String detail;

    EtlRequestError(
            HttpStatus status,
            String errorCode,
            String type,
            String title,
            String detail
    ) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.type = URI.create(Objects.requireNonNull(type, "type must not be null"));
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.detail = Objects.requireNonNull(detail, "detail must not be null");
    }

    /**
     * Returns the HTTP status for this deterministic request failure.
     *
     * @return immutable HTTP status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Returns the stable snake_case machine code.
     *
     * @return compatibility-safe error code
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * Returns the stable RFC 9457 problem type URI.
     *
     * @return problem type URI
     */
    public URI type() {
        return type;
    }

    /**
     * Returns the fixed human-readable category title.
     *
     * @return problem title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the fixed non-sensitive client guidance.
     *
     * @return problem detail
     */
    public String detail() {
        return detail;
    }
}
