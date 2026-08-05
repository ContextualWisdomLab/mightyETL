-- Support oldest-first claim selection across pending and expired-running durable jobs.
-- CONCURRENTLY preserves inserts, updates, and deletes while PostgreSQL builds the index.
-- The companion .sql.conf disables Flyway's per-migration transaction because PostgreSQL
-- rejects CREATE INDEX CONCURRENTLY inside a transaction block.
CREATE INDEX CONCURRENTLY etl_job_claim_eligibility_index
    ON etl_job_records (
        job_status,
        lease_expires_at,
        created_at,
        job_record_id
    );
