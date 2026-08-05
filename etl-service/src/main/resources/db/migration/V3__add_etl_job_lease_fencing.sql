ALTER TABLE etl_job_records
    ADD COLUMN lease_claim_id UUID,
    ADD COLUMN lease_owner_id VARCHAR(128),
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

-- Repair legacy rows before enforcing the stronger failure lifecycle invariant.
UPDATE etl_job_records
SET failure_code = 'etl_legacy_failure'
WHERE job_status = 'FAILED'
  AND failure_code IS NULL;

UPDATE etl_job_records
SET failure_code = NULL
WHERE job_status <> 'FAILED'
  AND failure_code IS NOT NULL;

ALTER TABLE etl_job_records
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
    );

CREATE INDEX etl_job_claim_eligibility_index
    ON etl_job_records (
        job_status,
        lease_expires_at,
        created_at,
        job_record_id
    );
