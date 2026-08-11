-- Support deterministic newest-first keyset pagination inside one hashed principal namespace.
-- CONCURRENTLY preserves inserts, updates, and deletes while PostgreSQL builds the index.
-- The companion .sql.conf disables Flyway's per-migration transaction because PostgreSQL
-- rejects CREATE INDEX CONCURRENTLY inside a transaction block.
CREATE INDEX CONCURRENTLY etl_job_owner_pagination_index
    ON etl_job_records (
        principal_scope_hash,
        created_at DESC,
        job_record_id DESC
    );
