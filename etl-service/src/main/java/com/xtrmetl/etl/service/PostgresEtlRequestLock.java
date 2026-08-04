package com.xtrmetl.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Uses a PostgreSQL transaction-level advisory lock to serialize one idempotency key.
 *
 * <p>The first 64 bits of the full SHA-256 key hash select an application-defined advisory lock.
 * A rare prefix collision can only serialize unrelated requests; correctness still relies on the
 * full 256-bit hash stored as the ledger primary key. PostgreSQL releases the advisory lock
 * automatically when the surrounding transaction commits or rolls back.</p>
 */
@Component
public class PostgresEtlRequestLock implements EtlRequestLock {

    private static final int ADVISORY_HASH_HEX_LENGTH = 16;
    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(?)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the PostgreSQL request lock adapter.
     *
     * @param jdbcTemplate database access that participates in the current transaction
     */
    public PostgresEtlRequestLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Waits for the transaction-level advisory lock derived from the scoped key hash.
     *
     * @param idempotencyKeyHash lowercase 64-character SHA-256 hash
     */
    @Override
    public void lock(String idempotencyKeyHash) {
        String hash = Objects.requireNonNull(
                idempotencyKeyHash,
                "idempotencyKeyHash must not be null"
        );
        if (hash.length() != 64 || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("idempotencyKeyHash must be lowercase SHA-256 hex");
        }

        long advisoryKey = Long.parseUnsignedLong(
                hash.substring(0, ADVISORY_HASH_HEX_LENGTH),
                16
        );
        jdbcTemplate.query(LOCK_SQL, resultSet -> null, advisoryKey);
    }
}
