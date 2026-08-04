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
    ),

    /** The optional Idempotency-Key does not satisfy mightyETL's bounded safe profile. */
    INVALID_IDEMPOTENCY_KEY(
            HttpStatus.BAD_REQUEST,
            "etl_invalid_idempotency_key",
            "urn:mightyetl:problem:etl-invalid-idempotency-key",
            "Invalid ETL idempotency key",
            "Idempotency-Key must be a quoted Structured Field String or a supported legacy raw value containing 16 to 128 safe ASCII characters."
    ),

    /** An idempotency-protected request has no authenticated principal namespace. */
    IDEMPOTENCY_PRINCIPAL_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "etl_idempotency_principal_required",
            "urn:mightyetl:problem:etl-idempotency-principal-required",
            "ETL idempotency authentication required",
            "An authenticated principal is required when Idempotency-Key is supplied."
    ),

    /** A scoped idempotency key was already committed for a different request payload. */
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "etl_idempotency_key_reused",
            "urn:mightyetl:problem:etl-idempotency-key-reused",
            "ETL idempotency key reused",
            "The Idempotency-Key was already used with a different request payload."
    ),

    /** Another transaction is still processing the same principal-scoped idempotency key. */
    IDEMPOTENCY_REQUEST_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "etl_idempotency_request_in_progress",
            "urn:mightyetl:problem:etl-idempotency-request-in-progress",
            "ETL idempotency request in progress",
            "A request with the same principal-scoped Idempotency-Key is still being processed."
    ),

    /** A durable job submission key already identifies different JSON text for this principal. */
    JOB_SUBMISSION_KEY_REUSED(
            HttpStatus.CONFLICT,
            "etl_job_submission_key_reused",
            "urn:mightyetl:problem:etl-job-submission-key-reused",
            "ETL job submission key reused",
            "The Idempotency-Key already identifies a different durable ETL job payload."
    ),

    /** Another transaction is currently creating the same principal-scoped durable job. */
    JOB_SUBMISSION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "etl_job_submission_in_progress",
            "urn:mightyetl:problem:etl-job-submission-in-progress",
            "ETL job submission in progress",
            "A durable ETL job with the same principal-scoped Idempotency-Key is being created."
    ),

    /** The requested job does not exist in the authenticated principal's namespace. */
    JOB_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "etl_job_not_found",
            "urn:mightyetl:problem:etl-job-not-found",
            "ETL job not found",
            "The requested ETL job was not found."
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
