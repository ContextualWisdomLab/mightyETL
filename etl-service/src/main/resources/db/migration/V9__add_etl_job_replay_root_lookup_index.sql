-- Support replay-root foreign-key enforcement and immutable-evidence descendant lookup.
-- CONCURRENTLY preserves durable-job inserts, lifecycle updates, and deletes during rollout.
-- The companion .sql.conf disables Flyway's per-migration transaction because PostgreSQL
-- rejects CREATE INDEX CONCURRENTLY inside a transaction block.
CREATE INDEX CONCURRENTLY etl_job_replay_root_lookup_index
    ON etl_job_records (
        replay_root_job_record_id,
        principal_scope_hash
    )
    WHERE replay_root_job_record_id IS NOT NULL;
