-- Rehearse V7 owner isolation, immutable lineage, deletion protection, and rollback.
-- This script runs only against the disposable PostgreSQL integration-test database.

BEGIN;

INSERT INTO etl_job_records (
    job_record_id,
    principal_scope_hash,
    submission_key_hash,
    request_digest,
    request_payload,
    job_status
) VALUES
    (
        '00000000-0000-4000-8000-000000000001',
        repeat('a', 64),
        repeat('1', 64),
        repeat('a', 64),
        '{}',
        'PENDING'
    ),
    (
        '00000000-0000-4000-8000-000000000002',
        repeat('a', 64),
        repeat('2', 64),
        repeat('b', 64),
        NULL,
        'FAILED'
    ),
    (
        '00000000-0000-4000-8000-000000000003',
        repeat('b', 64),
        repeat('3', 64),
        repeat('c', 64),
        '{}',
        'PENDING'
    ),
    (
        '00000000-0000-4000-8000-000000000004',
        repeat('b', 64),
        repeat('4', 64),
        repeat('d', 64),
        NULL,
        'CANCELLED'
    );

UPDATE etl_job_records
SET failure_code = 'etl_replay_source_failed'
WHERE job_record_id = '00000000-0000-4000-8000-000000000002';

UPDATE etl_job_records
SET cancellation_key_hash = repeat('e', 64),
    cancellation_code = 'etl_job_cancelled_by_owner',
    job_cancelled_at = CURRENT_TIMESTAMP
WHERE job_record_id = '00000000-0000-4000-8000-000000000004';

INSERT INTO etl_job_records (
    job_record_id,
    principal_scope_hash,
    submission_key_hash,
    request_digest,
    request_payload,
    job_status,
    replay_source_job_record_id,
    replay_root_job_record_id,
    replay_generation_count
) VALUES (
    '00000000-0000-4000-8000-000000000005',
    repeat('a', 64),
    repeat('5', 64),
    repeat('f', 64),
    '{}',
    'PENDING',
    '00000000-0000-4000-8000-000000000002',
    '00000000-0000-4000-8000-000000000001',
    1
);

DO $cross_owner_source_check$
BEGIN
    BEGIN
        INSERT INTO etl_job_records (
            job_record_id,
            principal_scope_hash,
            submission_key_hash,
            request_digest,
            request_payload,
            job_status,
            replay_source_job_record_id,
            replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000006',
            repeat('b', 64),
            repeat('6', 64),
            repeat('6', 64),
            '{}',
            'PENDING',
            '00000000-0000-4000-8000-000000000002',
            '00000000-0000-4000-8000-000000000003',
            1
        );
    EXCEPTION
        WHEN foreign_key_violation THEN
            NULL;
    END;

    IF EXISTS (
        SELECT 1
        FROM etl_job_records
        WHERE job_record_id = '00000000-0000-4000-8000-000000000006'
    ) THEN
        RAISE EXCEPTION 'cross-owner source lineage was accepted';
    END IF;
END
$cross_owner_source_check$;

DO $cross_owner_root_check$
BEGIN
    BEGIN
        INSERT INTO etl_job_records (
            job_record_id,
            principal_scope_hash,
            submission_key_hash,
            request_digest,
            request_payload,
            job_status,
            replay_source_job_record_id,
            replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000007',
            repeat('b', 64),
            repeat('7', 64),
            repeat('7', 64),
            '{}',
            'PENDING',
            '00000000-0000-4000-8000-000000000004',
            '00000000-0000-4000-8000-000000000001',
            1
        );
    EXCEPTION
        WHEN foreign_key_violation THEN
            NULL;
    END;

    IF EXISTS (
        SELECT 1
        FROM etl_job_records
        WHERE job_record_id = '00000000-0000-4000-8000-000000000007'
    ) THEN
        RAISE EXCEPTION 'cross-owner root lineage was accepted';
    END IF;
END
$cross_owner_root_check$;

DO $delete_restrict_check$
BEGIN
    BEGIN
        DELETE FROM etl_job_records
        WHERE job_record_id = '00000000-0000-4000-8000-000000000002';
        RAISE EXCEPTION 'ON DELETE RESTRICT did not protect replay history';
    EXCEPTION
        WHEN foreign_key_violation THEN
            NULL;
    END;

    BEGIN
        DELETE FROM etl_job_records
        WHERE job_record_id = '00000000-0000-4000-8000-000000000001';
        RAISE EXCEPTION 'ON DELETE RESTRICT did not protect replay history';
    EXCEPTION
        WHEN foreign_key_violation THEN
            NULL;
    END;
END
$delete_restrict_check$;

ROLLBACK;

BEGIN;

ALTER TABLE etl_job_records DROP CONSTRAINT etl_job_replay_source_reference;
ALTER TABLE etl_job_records DROP CONSTRAINT etl_job_replay_root_reference;
ALTER TABLE etl_job_records DROP CONSTRAINT etl_job_replay_lineage_complete_check;
ALTER TABLE etl_job_records DROP CONSTRAINT etl_job_owner_identity_unique;
ALTER TABLE etl_job_records DROP COLUMN replay_source_job_record_id;
ALTER TABLE etl_job_records DROP COLUMN replay_root_job_record_id;
ALTER TABLE etl_job_records DROP COLUMN replay_generation_count;

ROLLBACK;

DO $rollback_restoration_check$
DECLARE
    restored_column_count integer;
    restored_constraint_count integer;
BEGIN
    SELECT count(*)
      INTO restored_column_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'etl_job_records'
       AND column_name IN (
            'replay_source_job_record_id',
            'replay_root_job_record_id',
            'replay_generation_count'
       );

    SELECT count(*)
      INTO restored_constraint_count
      FROM pg_constraint AS constraint_record
      JOIN pg_class AS table_record
        ON table_record.oid = constraint_record.conrelid
     WHERE table_record.relname = 'etl_job_records'
       AND constraint_record.conname IN (
            'etl_job_replay_source_reference',
            'etl_job_replay_root_reference',
            'etl_job_replay_lineage_complete_check',
            'etl_job_owner_identity_unique'
       );

    IF restored_column_count <> 3 OR restored_constraint_count <> 4 THEN
        RAISE EXCEPTION 'rollback rehearsal did not restore V7';
    END IF;
END
$rollback_restoration_check$;
