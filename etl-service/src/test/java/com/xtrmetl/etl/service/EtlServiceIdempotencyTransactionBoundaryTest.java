package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that durable idempotency preserves its transaction and admission-control boundaries.
 *
 * <p>Spring applies {@code @Transactional} through a proxy. A directly constructed service does
 * not receive that proxy, so allowing the idempotent method to continue would release a PostgreSQL
 * transaction advisory lock too early and could separate target writes from the response ledger.
 * The production method therefore has to fail before lock or database access when no transaction
 * is active.</p>
 *
 * <p>Keyed requests must also enforce key and payload admission before computing their durable
 * decision through the request lock or ledger. This preserves the same zero-database-work boundary
 * as the unkeyed endpoint.</p>
 */
class EtlServiceIdempotencyTransactionBoundaryTest {

    /**
     * Proves direct invocation fails closed before any request lock or JDBC operation.
     */
    @Test
    void refusesIdempotentProcessingWithoutAnActiveTransactionBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlService etlService = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                requestLock
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> etlService.processDataIdempotently(
                        "[{\"id\":\"record_alpha\"}]",
                        "550e8400-e29b-41d4-a716-446655440000",
                        "tenant_alpha"
                )
        );

        assertEquals(
                "Idempotent ETL processing requires an active transaction",
                exception.getMessage()
        );
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    /**
     * Proves a missing key is rejected before transaction, request-lock, or JDBC work.
     */
    @Test
    void rejectsMissingIdempotencyKeyBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlService etlService = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                requestLock
        );

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlService.processDataIdempotently(
                        "[{\"id\":\"record_alpha\"}]",
                        null,
                        "tenant_alpha"
                )
        );

        assertEquals(EtlRequestError.INVALID_IDEMPOTENCY_KEY, exception.error());
        verifyNoInteractions(requestLock, jdbcTemplate);
    }

    /**
     * Proves an oversized keyed payload is rejected before request-lock or ledger access.
     */
    @Test
    void rejectsOversizedKeyedPayloadBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlRequestLock requestLock = mock(EtlRequestLock.class);
        EtlBatchProperties batchProperties = new EtlBatchProperties();
        batchProperties.setMaxPayloadBytes(8);
        EtlService etlService = new EtlService(
                jdbcTemplate,
                new ObjectMapper(),
                batchProperties,
                requestLock
        );

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> etlService.processDataIdempotently(
                            "[{\"id\":\"record_alpha\"}]",
                            "550e8400-e29b-41d4-a716-446655440000",
                            "tenant_alpha"
                    )
            );

            assertEquals(EtlRequestError.PAYLOAD_TOO_LARGE, exception.error());
            verifyNoInteractions(requestLock, jdbcTemplate);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
