package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Guards the atomicity boundary of the durable-job success transition.
 *
 * <p>Marking a job successful is valid only in the database transaction that also contains the
 * target effects and response-ledger write. A direct repository call without an actual transaction
 * could otherwise publish a false terminal success after unrelated or absent target work.</p>
 */
class EtlJobLeaseSuccessTransactionBoundaryTest {

    /**
     * Proves success fails closed before JDBC when no caller-owned transaction is active.
     */
    @Test
    void refusesSuccessWithoutAnActiveTransactionBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        EtlJobLeaseRepository repository = new EtlJobLeaseRepository(
                jdbcTemplate,
                transactionManager
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.markSucceeded(sampleLease())
        );

        assertEquals(
                "Durable ETL success requires an active transaction",
                exception.getMessage()
        );
        verifyNoInteractions(jdbcTemplate, transactionManager);
    }

    private static EtlJobLease sampleLease() {
        return new EtlJobLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "worker-alpha",
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "[{\"id\":\"record_alpha\"}]",
                1,
                Instant.now().plusSeconds(300)
        );
    }
}
