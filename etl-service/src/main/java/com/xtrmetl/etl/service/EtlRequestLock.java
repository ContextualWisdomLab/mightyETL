package com.xtrmetl.etl.service;

/**
 * Serializes idempotency decisions for one scoped client key inside the current transaction.
 *
 * <p>Implementations must block competing transactions that use the same full SHA-256 key hash.
 * The lock must remain effective until the surrounding transaction completes so the ledger lookup,
 * ETL writes, and ledger insert form one race-free decision.</p>
 */
@FunctionalInterface
public interface EtlRequestLock {

    /**
     * Acquires the transaction-lifetime lock for one scoped idempotency key hash.
     *
     * @param idempotencyKeyHash lowercase 64-character SHA-256 hash
     */
    void lock(String idempotencyKeyHash);
}
