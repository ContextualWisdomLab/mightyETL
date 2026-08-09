-- Local docker-compose schema bootstrap (primary + replica)
--
-- This default clean-install path intentionally contains only the ETL target schema that
-- protected mightyETL actually uses. Historical local-auth tables were never backed by a shipped
-- authentication API and no longer run implicitly on new installations. Existing PostgreSQL
-- volumes are not modified by this bootstrap change. See docker/postgres/compat/legacy_auth_tables.sql
-- for the explicit deprecated compatibility artifact.

CREATE TABLE IF NOT EXISTS processed_data (
    id BIGSERIAL PRIMARY KEY,
    data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

