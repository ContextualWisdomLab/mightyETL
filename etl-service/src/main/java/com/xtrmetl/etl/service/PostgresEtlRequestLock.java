package com.xtrmetl.etl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Uses a PostgreSQL transaction-level try-advisory lock for one idempotency key.
 *
 * <p>The first 64 bits of the full SHA-256 key hash select an application-defined advisory lock.
 * A rare prefix collision can reject an unrelated concurrent request, but correctness still relies
 * on the full 256-bit hash stored as the ledger primary key. PostgreSQL releases an acquired lock
 * automatically when the surrounding transaction commits or rolls back.</p>
 */
@Component
public class PostgresEtlRequestLock implements EtlRequestLock {

    private static final int ADVISORY_HASH_HEX_LENGTH = 16;
    private static final String LOCK_SQL = "SELECT pg_try_advisory_xact_lock(?)";

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
     * Attempts the transaction-level advisory lock derived from the scoped key hash.
     *
     * @param idempotencyKeyHash lowercase 64-character SHA-256 hash
     * @return {@code true} when acquired; {@code false} when another transaction owns the lock
     */
    @Override
    public boolean tryLock(String idempotencyKeyHash) {
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
        Boolean acquired = jdbcTemplate.queryForObject(LOCK_SQL, Boolean.class, advisoryKey);
        if (acquired == null) {
            throw new IllegalStateException("PostgreSQL advisory lock query returned null");
        }
        return acquired;
    }
}
