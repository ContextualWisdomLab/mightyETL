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
            HttpStatus.UNPROCESSABLE_ENTITY,
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

    /** The supplied durable-job list page size is not a canonical integer in the supported range. */
    INVALID_JOB_PAGE_LIMIT(
            HttpStatus.BAD_REQUEST,
            "etl_invalid_job_page_limit",
            "urn:mightyetl:problem:etl-invalid-job-page-limit",
            "Invalid ETL job page limit",
            "The ETL job page limit must be a canonical integer from 1 through 100."
    ),

    /** The supplied durable-job list cursor is malformed, non-canonical, or unsupported. */
    INVALID_JOB_PAGE_CURSOR(
            HttpStatus.BAD_REQUEST,
            "etl_invalid_job_page_cursor",
            "urn:mightyetl:problem:etl-invalid-job-page-cursor",
            "Invalid ETL job page cursor",
            "The ETL job page cursor is invalid or uses an unsupported opaque format."
    ),

    /** The cancellation key is absent or outside the bounded safe idempotency profile. */
    JOB_CANCELLATION_KEY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "etl_job_cancellation_key_required",
            "urn:mightyetl:problem:etl-job-cancellation-key-required",
            "ETL job cancellation key required",
            "Cancellation requires a supported principal-scoped Idempotency-Key."
    ),

    /** The replay key is absent or outside the bounded safe idempotency profile. */
    JOB_REPLAY_KEY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "etl_job_replay_key_required",
            "urn:mightyetl:problem:etl-job-replay-key-required",
            "ETL job replay key required",
            "Replay requires a supported principal-scoped Idempotency-Key."
    ),

    /** The resupplied payload does not match the immutable terminal source digest. */
    JOB_REPLAY_PAYLOAD_MISMATCH(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "etl_job_replay_payload_mismatch",
            "urn:mightyetl:problem:etl-job-replay-payload-mismatch",
            "ETL job replay payload mismatch",
            "The replay payload does not match the immutable source job payload digest."
    ),

    /** A replay idempotency key already identifies a different source or payload. */
    JOB_REPLAY_KEY_REUSED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "etl_job_replay_key_reused",
            "urn:mightyetl:problem:etl-job-replay-key-reused",
            "ETL job replay key reused",
            "The Idempotency-Key already identifies a different durable job replay."
    ),

    /** Another transaction owns the same principal-scoped replay identity. */
    JOB_REPLAY_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "etl_job_replay_in_progress",
            "urn:mightyetl:problem:etl-job-replay-in-progress",
            "ETL job replay in progress",
            "A durable job replay with the same principal-scoped Idempotency-Key is being created."
    ),

    /** A pending or running source is still active and cannot be replayed. */
    JOB_REPLAY_SOURCE_ACTIVE(
            HttpStatus.CONFLICT,
            "etl_job_replay_source_active",
            "urn:mightyetl:problem:etl-job-replay-source-active",
            "ETL job replay source active",
            "Pending or running durable jobs cannot be replayed."
    ),

    /** A succeeded source is excluded to prevent silent duplicate target effects. */
    JOB_REPLAY_SOURCE_SUCCEEDED(
            HttpStatus.CONFLICT,
            "etl_job_replay_source_succeeded",
            "urn:mightyetl:problem:etl-job-replay-source-succeeded",
            "ETL job replay source succeeded",
            "A succeeded durable job cannot be replayed through this endpoint."
    ),

    /** The durable job was already cancelled with a different cancellation key. */
    JOB_CANCELLATION_KEY_REUSED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "etl_job_cancellation_key_reused",
            "urn:mightyetl:problem:etl-job-cancellation-key-reused",
            "ETL job cancellation key reused",
            "The durable job was already cancelled with a different Idempotency-Key."
    ),

    /** An eligible job remained active after the authoritative cancellation update. */
    JOB_CANCELLATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "etl_job_cancellation_in_progress",
            "urn:mightyetl:problem:etl-job-cancellation-in-progress",
            "ETL job cancellation in progress",
            "The durable job cancellation could not yet establish a terminal outcome."
    ),

    /** Durable success committed before the cancellation transition. */
    JOB_ALREADY_SUCCEEDED(
            HttpStatus.CONFLICT,
            "etl_job_already_succeeded",
            "urn:mightyetl:problem:etl-job-already-succeeded",
            "ETL job already succeeded",
            "The durable job succeeded before cancellation could commit."
    ),

    /** Durable failure committed before the cancellation transition. */
    JOB_ALREADY_FAILED(
            HttpStatus.CONFLICT,
            "etl_job_already_failed",
            "urn:mightyetl:problem:etl-job-already-failed",
            "ETL job already failed",
            "The durable job failed before cancellation could commit."
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
