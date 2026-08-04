-- Durable principal-scoped asynchronous ETL job intake.
-- Raw authenticated principal names and Idempotency-Key values are never stored.
CREATE TABLE etl_job_records (
    job_record_id UUID PRIMARY KEY,
    principal_scope_hash CHAR(64) NOT NULL,
    submission_key_hash CHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    request_payload TEXT,
    job_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT etl_job_principal_scope_hash_format CHECK (
        principal_scope_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_job_submission_key_hash_format CHECK (
        submission_key_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_job_request_digest_format CHECK (
        request_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_job_status_value_check CHECK (
        job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT etl_job_payload_lifecycle_check CHECK (
        (job_status IN ('PENDING', 'RUNNING') AND request_payload IS NOT NULL)
        OR (job_status IN ('SUCCEEDED', 'FAILED') AND request_payload IS NULL)
    ),
    CONSTRAINT etl_job_attempt_count_nonnegative CHECK (
        attempt_count >= 0
    ),
    CONSTRAINT etl_job_failure_code_format CHECK (
        failure_code IS NULL OR failure_code ~ '^[a-z][a-z0-9_]{2,127}$'
    ),
    CONSTRAINT etl_job_submission_scope_unique
        UNIQUE (principal_scope_hash, submission_key_hash)
);

CREATE INDEX etl_job_status_created_index
    ON etl_job_records (job_status, created_at);
