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

-- Keep the relational lineage authoritative even when rows are imported outside the service.
-- Each derived row must point to an exact terminal predecessor, preserve the first root, and
-- advance the generation by exactly one. Lineage columns become immutable after insertion so a
-- later update cannot silently rewrite descendants' provenance.
CREATE FUNCTION validate_etl_job_replay_lineage() RETURNS trigger
LANGUAGE plpgsql
AS $etl_job_replay_lineage$
DECLARE
    source_job_status VARCHAR(32);
    source_source_job_record_id UUID;
    source_root_job_record_id UUID;
    source_generation_count INTEGER;
    root_job_status VARCHAR(32);
    root_source_job_record_id UUID;
    root_root_job_record_id UUID;
    root_generation_count INTEGER;
BEGIN
    IF TG_OP = 'UPDATE'
       AND (
            OLD.replay_source_job_record_id
                IS DISTINCT FROM NEW.replay_source_job_record_id
            OR OLD.replay_root_job_record_id
                IS DISTINCT FROM NEW.replay_root_job_record_id
            OR OLD.replay_generation_count
                IS DISTINCT FROM NEW.replay_generation_count
       ) THEN
        RAISE EXCEPTION 'Replay lineage fields are immutable'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.replay_generation_count IS NULL THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT'
       AND (
            NEW.job_status <> 'PENDING'
            OR NEW.attempt_count <> 0
            OR NEW.request_payload IS NULL
       ) THEN
        RAISE EXCEPTION 'Replay rows must start as pending jobs'
            USING ERRCODE = '23514';
    END IF;

    SELECT job_status,
           replay_source_job_record_id,
           replay_root_job_record_id,
           replay_generation_count
      INTO source_job_status,
           source_source_job_record_id,
           source_root_job_record_id,
           source_generation_count
      FROM etl_job_records
     WHERE job_record_id = NEW.replay_source_job_record_id
       AND principal_scope_hash = NEW.principal_scope_hash
     FOR KEY SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Replay source is missing or belongs to another owner'
            USING ERRCODE = '23514';
    END IF;

    IF source_job_status NOT IN ('FAILED', 'CANCELLED') THEN
        RAISE EXCEPTION 'Replay source must be failed or cancelled'
            USING ERRCODE = '23514';
    END IF;

    SELECT job_status,
           replay_source_job_record_id,
           replay_root_job_record_id,
           replay_generation_count
      INTO root_job_status,
           root_source_job_record_id,
           root_root_job_record_id,
           root_generation_count
      FROM etl_job_records
     WHERE job_record_id = NEW.replay_root_job_record_id
       AND principal_scope_hash = NEW.principal_scope_hash
     FOR KEY SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Replay root is missing or belongs to another owner'
            USING ERRCODE = '23514';
    END IF;

    IF root_job_status NOT IN ('FAILED', 'CANCELLED')
       OR root_source_job_record_id IS NOT NULL
       OR root_root_job_record_id IS NOT NULL
       OR root_generation_count IS NOT NULL THEN
        RAISE EXCEPTION 'Replay root is not a lineage root'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.replay_generation_count = 1 THEN
        IF NEW.replay_source_job_record_id <> NEW.replay_root_job_record_id THEN
            RAISE EXCEPTION 'Generation one must reference the same source and root'
                USING ERRCODE = '23514';
        END IF;
    ELSIF source_source_job_record_id IS NULL
          OR source_root_job_record_id
                IS DISTINCT FROM NEW.replay_root_job_record_id
          OR source_generation_count
                IS DISTINCT FROM NEW.replay_generation_count - 1 THEN
        RAISE EXCEPTION 'Replay generation does not follow the immediate source'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$etl_job_replay_lineage$;

CREATE TRIGGER etl_job_replay_lineage_guard_trigger
BEFORE INSERT OR UPDATE OF replay_source_job_record_id,
    replay_root_job_record_id, replay_generation_count
ON etl_job_records
FOR EACH ROW
EXECUTE FUNCTION validate_etl_job_replay_lineage();
