-- Durable request ledger for optional principal-scoped Idempotency-Key processing.
-- Raw client keys, authenticated principal names, and request payloads are never stored.
CREATE TABLE etl_idempotency_records (
    idempotency_key_hash CHAR(64) PRIMARY KEY,
    request_digest CHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT etl_idempotency_key_hash_format CHECK (
        idempotency_key_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT etl_request_digest_format CHECK (
        request_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX etl_idempotency_created_at_index
    ON etl_idempotency_records (created_at);
