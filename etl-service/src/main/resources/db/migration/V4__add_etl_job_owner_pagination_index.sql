-- Support deterministic newest-first keyset pagination inside one hashed principal namespace.
-- PostgreSQL can scan this B-tree in either direction; explicit DESC documents the API order.
CREATE INDEX etl_job_owner_pagination_index
    ON etl_job_records (
        principal_scope_hash,
        created_at DESC,
        job_record_id DESC
    );
