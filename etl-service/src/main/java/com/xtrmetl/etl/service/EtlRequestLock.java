package com.xtrmetl.etl.service;

/**
 * Attempts to serialize one scoped idempotency decision inside the current transaction.
 *
 * <p>Implementations must return immediately. A successful acquisition must remain effective until
 * the surrounding transaction completes so ledger lookup, ETL writes, and ledger insertion form
 * one race-free decision. A failed acquisition reports that a competing transaction currently owns
 * the same lock and must not perform ledger or target work.</p>
 */
@FunctionalInterface
public interface EtlRequestLock {

    /**
     * Attempts to acquire the transaction-lifetime lock for one scoped idempotency key hash.
     *
     * @param idempotencyKeyHash lowercase 64-character SHA-256 hash
     * @return {@code true} when the current transaction acquired the lock; {@code false} when a
     *         competing transaction already owns it
     */
    boolean tryLock(String idempotencyKeyHash);
}
