-- Rehearse V7 lineage authority plus V8/V9 replay lookup indexes on PostgreSQL 18.
-- This script runs only against the disposable PostgreSQL integration-test database.

DO $migration_object_check$
DECLARE
    trigger_count integer;
    function_count integer;
    lookup_index_count integer;
BEGIN
    SELECT count(*)
      INTO trigger_count
      FROM pg_trigger AS trigger_record
      JOIN pg_class AS table_record
        ON table_record.oid = trigger_record.tgrelid
     WHERE table_record.relname = 'etl_job_records'
       AND trigger_record.tgname = 'etl_job_replay_lineage_guard_trigger'
       AND NOT trigger_record.tgisinternal;

    SELECT count(*)
      INTO function_count
      FROM pg_proc AS function_record
     WHERE function_record.proname = 'validate_etl_job_replay_lineage';

    SELECT count(*)
      INTO lookup_index_count
      FROM pg_class AS index_class
      JOIN pg_index AS index_record
        ON index_record.indexrelid = index_class.oid
     WHERE index_class.relname IN (
            'etl_job_replay_source_lookup_index',
            'etl_job_replay_root_lookup_index'
       )
       AND index_record.indisready
       AND index_record.indisvalid;

    IF trigger_count <> 1 OR function_count <> 1 OR lookup_index_count <> 2 THEN
        RAISE EXCEPTION 'replay lineage trigger or function is missing';
    END IF;
END
$migration_object_check$;

BEGIN;

-- Pending controls for owner A and owner B.
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
        '00000000-0000-4000-8000-000000000003',
        repeat('b', 64),
        repeat('3', 64),
        repeat('c', 64),
        '{}',
        'PENDING'
    );

-- Two independent same-owner terminal roots plus one terminal root for owner B.
INSERT INTO etl_job_records (
    job_record_id,
    principal_scope_hash,
    submission_key_hash,
    request_digest,
    request_payload,
    job_status,
    failure_code
) VALUES
    (
        '00000000-0000-4000-8000-000000000002',
        repeat('a', 64),
        repeat('2', 64),
        repeat('b', 64),
        NULL,
        'FAILED',
        'etl_replay_source_failed'
    ),
    (
        '00000000-0000-4000-8000-000000000014',
        repeat('a', 64),
        repeat('e', 64),
        repeat('b', 64),
        NULL,
        'FAILED',
        'etl_alternate_root_failed'
    );

INSERT INTO etl_job_records (
    job_record_id,
    principal_scope_hash,
    submission_key_hash,
    request_digest,
    request_payload,
    job_status,
    cancellation_key_hash,
    cancellation_code,
    job_cancelled_at
) VALUES (
    '00000000-0000-4000-8000-000000000004',
    repeat('b', 64),
    repeat('4', 64),
    repeat('d', 64),
    NULL,
    'CANCELLED',
    repeat('e', 64),
    'etl_job_cancelled_by_owner',
    CURRENT_TIMESTAMP
);

-- Create a valid first-generation replay with the exact source digest, then terminalize it.
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
    repeat('b', 64),
    '{}',
    'PENDING',
    '00000000-0000-4000-8000-000000000002',
    '00000000-0000-4000-8000-000000000002',
    1
);

UPDATE etl_job_records
   SET job_status = 'FAILED',
       request_payload = NULL,
       failure_code = 'etl_replay_generation_failed'
 WHERE job_record_id = '00000000-0000-4000-8000-000000000005';

-- A valid second generation retains the first root, source digest, and one-step succession.
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
    '00000000-0000-4000-8000-000000000008',
    repeat('a', 64),
    repeat('8', 64),
    repeat('b', 64),
    '{}',
    'PENDING',
    '00000000-0000-4000-8000-000000000005',
    '00000000-0000-4000-8000-000000000002',
    2
);

DO $lineage_rejection_checks$
BEGIN
    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000009',
            repeat('a', 64), repeat('9', 64), repeat('a', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000001',
            '00000000-0000-4000-8000-000000000001', 1
        );
        RAISE EXCEPTION 'nonterminal replay source was accepted';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000010',
            repeat('a', 64), repeat('a', 64), repeat('b', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000002',
            '00000000-0000-4000-8000-000000000014', 1
        );
        RAISE EXCEPTION 'generation-one replay accepted a different root';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000011',
            repeat('a', 64), repeat('b', 64), repeat('b', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000005',
            '00000000-0000-4000-8000-000000000005', 2
        );
        RAISE EXCEPTION 'a derived replay row was accepted as lineage root';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000012',
            repeat('a', 64), repeat('c', 64), repeat('b', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000005',
            '00000000-0000-4000-8000-000000000002', 3
        );
        RAISE EXCEPTION 'a skipped replay generation was accepted';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000006',
            repeat('b', 64), repeat('6', 64), repeat('d', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000002',
            '00000000-0000-4000-8000-000000000004', 1
        );
        RAISE EXCEPTION 'cross-owner source lineage was accepted';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000007',
            repeat('b', 64), repeat('7', 64), repeat('d', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000004',
            '00000000-0000-4000-8000-000000000002', 1
        );
        RAISE EXCEPTION 'cross-owner root lineage was accepted';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;
END
$lineage_rejection_checks$;

DO $digest_continuity_check$
BEGIN
    BEGIN
        INSERT INTO etl_job_records (
            job_record_id, principal_scope_hash, submission_key_hash,
            request_digest, request_payload, job_status,
            replay_source_job_record_id, replay_root_job_record_id,
            replay_generation_count
        ) VALUES (
            '00000000-0000-4000-8000-000000000013',
            repeat('a', 64), repeat('d', 64), repeat('c', 64), '{}', 'PENDING',
            '00000000-0000-4000-8000-000000000002',
            '00000000-0000-4000-8000-000000000002', 1
        );
        RAISE EXCEPTION 'replay digest mismatch was accepted';
    EXCEPTION
        WHEN check_violation OR foreign_key_violation THEN NULL;
    END;
END
$digest_continuity_check$;

DO $immutability_checks$
BEGIN
    BEGIN
        UPDATE etl_job_records
           SET replay_root_job_record_id = '00000000-0000-4000-8000-000000000014'
         WHERE job_record_id = '00000000-0000-4000-8000-000000000005';
        RAISE EXCEPTION 'replay lineage fields were mutable';
    EXCEPTION
        WHEN check_violation THEN NULL;
    END;

    BEGIN
        UPDATE etl_job_records
           SET request_digest = repeat('0', 64)
         WHERE job_record_id = '00000000-0000-4000-8000-000000000002';
        RAISE EXCEPTION 'referenced replay root evidence was mutable';
    EXCEPTION
        WHEN check_violation THEN NULL;
    END;

    BEGIN
        UPDATE etl_job_records
           SET failure_code = 'etl_replay_generation_changed'
         WHERE job_record_id = '00000000-0000-4000-8000-000000000005';
        RAISE EXCEPTION 'referenced immediate-source evidence was mutable';
    EXCEPTION
        WHEN check_violation THEN NULL;
    END;
END
$immutability_checks$;

DO $delete_restrict_check$
BEGIN
    BEGIN
        DELETE FROM etl_job_records
         WHERE job_record_id = '00000000-0000-4000-8000-000000000005';
        RAISE EXCEPTION 'ON DELETE RESTRICT did not protect immediate replay history';
    EXCEPTION
        WHEN foreign_key_violation THEN NULL;
    END;

    BEGIN
        DELETE FROM etl_job_records
         WHERE job_record_id = '00000000-0000-4000-8000-000000000002';
        RAISE EXCEPTION 'ON DELETE RESTRICT did not protect replay root history';
    EXCEPTION
        WHEN foreign_key_violation THEN NULL;
    END;
END
$delete_restrict_check$;

ROLLBACK;

-- Rehearse ordered rollback without changing the migrated database.
BEGIN;

DROP INDEX etl_job_replay_source_lookup_index;
DROP INDEX etl_job_replay_root_lookup_index;
DROP TRIGGER etl_job_replay_lineage_guard_trigger ON etl_job_records;
DROP FUNCTION validate_etl_job_replay_lineage();
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
    restored_trigger_count integer;
    restored_function_count integer;
    restored_index_count integer;
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

    SELECT count(*)
      INTO restored_trigger_count
      FROM pg_trigger AS trigger_record
      JOIN pg_class AS table_record
        ON table_record.oid = trigger_record.tgrelid
     WHERE table_record.relname = 'etl_job_records'
       AND trigger_record.tgname = 'etl_job_replay_lineage_guard_trigger'
       AND NOT trigger_record.tgisinternal;

    SELECT count(*)
      INTO restored_function_count
      FROM pg_proc AS function_record
     WHERE function_record.proname = 'validate_etl_job_replay_lineage';

    SELECT count(*)
      INTO restored_index_count
      FROM pg_class AS index_class
      JOIN pg_index AS index_record
        ON index_record.indexrelid = index_class.oid
     WHERE index_class.relname IN (
            'etl_job_replay_source_lookup_index',
            'etl_job_replay_root_lookup_index'
       )
       AND index_record.indisready
       AND index_record.indisvalid;

    IF restored_column_count <> 3
       OR restored_constraint_count <> 4
       OR restored_trigger_count <> 1
       OR restored_function_count <> 1
       OR restored_index_count <> 2 THEN
        RAISE EXCEPTION 'rollback rehearsal did not restore V7';
    END IF;
END
$rollback_restoration_check$;
