package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that durable idempotency cannot silently run without a real transaction boundary.
 *
 * <p>Spring applies {@code @Transactional} through a proxy. A directly constructed service does
 * not receive that proxy, so allowing the idempotent method to continue would release a PostgreSQL
 * transaction advisory lock too early and could separate target writes from the response ledger.
 * The production method therefore has to fail before lock or database access when no transaction
 * is active.</p>
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
}
