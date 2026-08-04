package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Defines the immediate conflict boundary for concurrent principal-scoped idempotency requests.
 */
class EtlServiceIdempotencyConflictTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void rejectsAnUnavailableRequestLockBeforeLedgerOrTargetAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicReference<String> attemptedHash = new AtomicReference<>();
        EtlRequestLock requestLock = idempotencyKeyHash -> {
            attemptedHash.set(idempotencyKeyHash);
            return false;
        };
        EtlService service = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                requestLock
        );
        TransactionSynchronizationManager.setActualTransactionActive(true);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.processDataIdempotently(
                        "[{\"id\":\"record_alpha\"}]",
                        "\"550e8400-e29b-41d4-a716-446655440000\"",
                        "tenant_alpha"
                )
        );

        assertSame(EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS, exception.error());
        assertNotNull(attemptedHash.get());
        assertTrue(attemptedHash.get().matches("[0-9a-f]{64}"));
        verifyNoInteractions(jdbcTemplate);
    }
}
