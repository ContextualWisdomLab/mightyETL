package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards the durable response-ledger service from creating its own execution transaction.
 *
 * <p>The lease-fenced execution service owns the transaction that contains target effects, the
 * response ledger, and terminal success. A transactional annotation on the public ledger method
 * would let a direct Spring-proxy caller commit target and ledger effects without the success fence.</p>
 */
class EtlJobIdempotencyTransactionAnnotationTest {

    /**
     * Requires the public processing method to join, rather than create, the caller transaction.
     *
     * @throws NoSuchMethodException when the public durable processing contract is missing
     */
    @Test
    void processDoesNotCreateAStandaloneTransaction() throws NoSuchMethodException {
        Method processMethod = EtlJobIdempotencyService.class.getMethod(
                "process",
                EtlJobLease.class
        );

        assertNull(
                processMethod.getAnnotation(Transactional.class),
                "Durable job idempotency must not own a transaction"
        );
    }
}
