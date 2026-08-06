-- Add immutable source/root/generation lineage to replay-created durable job rows.
ALTER TABLE etl_job_records
    ADD COLUMN replay_source_job_record_id UUID,
    ADD COLUMN replay_root_job_record_id UUID,
    ADD COLUMN replay_generation_count INTEGER;

ALTER TABLE etl_job_records
    ADD CONSTRAINT etl_job_owner_identity_unique
        UNIQUE (job_record_id, principal_scope_hash),
    ADD CONSTRAINT etl_job_replay_source_reference
        FOREIGN KEY (replay_source_job_record_id, principal_scope_hash)
        REFERENCES etl_job_records (job_record_id, principal_scope_hash)
        ON DELETE RESTRICT,
    ADD CONSTRAINT etl_job_replay_root_reference
        FOREIGN KEY (replay_root_job_record_id, principal_scope_hash)
        REFERENCES etl_job_records (job_record_id, principal_scope_hash)
        ON DELETE RESTRICT,
    ADD CONSTRAINT etl_job_replay_lineage_complete_check CHECK (
        (
            replay_source_job_record_id IS NULL
            AND replay_root_job_record_id IS NULL
            AND replay_generation_count IS NULL
        )
        OR
        (
            replay_source_job_record_id IS NOT NULL
            AND replay_root_job_record_id IS NOT NULL
            AND replay_generation_count BETWEEN 1 AND 100
            AND replay_source_job_record_id <> job_record_id
            AND replay_root_job_record_id <> job_record_id
        )
    );
