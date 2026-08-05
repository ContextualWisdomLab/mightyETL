package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves that the lease repository rejects unsafe public arguments before database access.
 */
class EtlJobLeaseRepositoryValidationTest {

    @Test
    void rejectsLeaseDurationsAboveTheOperationalSafetyCeilingBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        EtlJobLeaseRepository repository = new EtlJobLeaseRepository(
                jdbcTemplate,
                transactionManager
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.claimNext(
                        "worker-alpha",
                        Duration.ofSeconds(
                                EtlJobWorkerProperties.MAXIMUM_LEASE_DURATION_SECONDS + 1L
                        ),
                        3
                )
        );
        verifyNoInteractions(jdbcTemplate, transactionManager);
    }
}
