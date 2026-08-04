-- Durable owner-scoped asynchronous ETL job queue and status resources.
-- Raw principal names and client idempotency keys are never stored.
CREATE TABLE etl_job_records (
    job_record_id UUID PRIMARY KEY,
    principal_scope_hash CHAR(64) NOT NULL,
    submission_key_hash CHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    request_payload TEXT,
    job_status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    lease_owner_id VARCHAR(128),
    lease_expires_at TIMESTAMPTZ,
    response_body TEXT,
    failure_code VARCHAR(128),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT etl_job_submission_key_unique UNIQUE (
        principal_scope_hash,
        submission_key_hash
    ),
    CONSTRAINT etl_job_principal_hash_format CHECK (
        principal_scope_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_job_submission_hash_format CHECK (
        submission_key_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_job_request_digest_format CHECK (
        request_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_job_status_value CHECK (
        job_status IN ('pending', 'running', 'succeeded', 'failed')
    ),
    CONSTRAINT etl_job_attempt_count_nonnegative CHECK (
        attempt_count >= 0
    ),
    CONSTRAINT etl_job_lease_state_consistency CHECK (
        (
            job_status = 'running'
            AND lease_owner_id IS NOT NULL
            AND lease_expires_at IS NOT NULL
        )
        OR
        (
            job_status <> 'running'
            AND lease_owner_id IS NULL
            AND lease_expires_at IS NULL
        )
    ),
    CONSTRAINT etl_job_terminal_payload_cleared CHECK (
        (
            job_status IN ('pending', 'running')
            AND request_payload IS NOT NULL
        )
        OR
        (
            job_status IN ('succeeded', 'failed')
            AND request_payload IS NULL
        )
    ),
    CONSTRAINT etl_job_terminal_result_consistency CHECK (
        (
            job_status IN ('pending', 'running')
            AND response_body IS NULL
            AND failure_code IS NULL
            AND completed_at IS NULL
        )
        OR
        (
            job_status = 'succeeded'
            AND response_body IS NOT NULL
            AND failure_code IS NULL
            AND completed_at IS NOT NULL
        )
        OR
        (
            job_status = 'failed'
            AND response_body IS NULL
            AND failure_code IS NOT NULL
            AND completed_at IS NOT NULL
        )
    )
);

CREATE INDEX etl_job_owner_lookup_index
    ON etl_job_records (principal_scope_hash, job_record_id);

CREATE INDEX etl_job_claim_order_index
    ON etl_job_records (
        job_status,
        lease_expires_at,
        submitted_at,
        job_record_id
    );
