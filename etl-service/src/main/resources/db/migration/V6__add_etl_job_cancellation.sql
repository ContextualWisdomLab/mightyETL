-- Add owner-scoped durable job cancellation without retaining raw principals or keys.
ALTER TABLE etl_job_records
    ADD COLUMN cancellation_key_hash CHAR(64),
    ADD COLUMN cancellation_code VARCHAR(128),
    ADD COLUMN job_cancelled_at TIMESTAMPTZ;

-- Replace lifecycle constraints so clean installations and upgrades converge on one state machine.
ALTER TABLE etl_job_records
    DROP CONSTRAINT etl_job_status_value_check,
    DROP CONSTRAINT etl_job_payload_lifecycle_check,
    DROP CONSTRAINT etl_job_lease_lifecycle_check,
    DROP CONSTRAINT etl_job_failure_lifecycle_check;

ALTER TABLE etl_job_records
    ADD CONSTRAINT etl_job_status_value_check CHECK (
        job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    ADD CONSTRAINT etl_job_payload_lifecycle_check CHECK (
        (
            job_status IN ('PENDING', 'RUNNING')
            AND request_payload IS NOT NULL
        )
        OR
        (
            job_status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
            AND request_payload IS NULL
        )
    ),
    ADD CONSTRAINT etl_job_lease_lifecycle_check CHECK (
        (
            job_status = 'RUNNING'
            AND lease_claim_id IS NOT NULL
            AND lease_owner_id IS NOT NULL
            AND lease_expires_at IS NOT NULL
        )
        OR
        (
            job_status <> 'RUNNING'
            AND lease_claim_id IS NULL
            AND lease_owner_id IS NULL
            AND lease_expires_at IS NULL
        )
    ),
    ADD CONSTRAINT etl_job_failure_lifecycle_check CHECK (
        (
            job_status = 'FAILED'
            AND failure_code IS NOT NULL
        )
        OR
        (
            job_status <> 'FAILED'
            AND failure_code IS NULL
        )
    ),
    ADD CONSTRAINT etl_job_cancellation_key_hash_format CHECK (
        cancellation_key_hash IS NULL
        OR cancellation_key_hash ~ '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT etl_job_cancellation_code_format CHECK (
        cancellation_code IS NULL
        OR cancellation_code ~ '^[a-z][a-z0-9_]{2,127}$'
    ),
    ADD CONSTRAINT etl_job_cancellation_code_value CHECK (
        cancellation_code IS NULL
        OR cancellation_code = 'etl_job_cancelled_by_owner'
    ),
    ADD CONSTRAINT etl_job_cancellation_lifecycle_check CHECK (
        (
            job_status = 'CANCELLED'
            AND cancellation_key_hash IS NOT NULL
            AND cancellation_code IS NOT NULL
            AND job_cancelled_at IS NOT NULL
        )
        OR
        (
            job_status <> 'CANCELLED'
            AND cancellation_key_hash IS NULL
            AND cancellation_code IS NULL
            AND job_cancelled_at IS NULL
        )
    );
