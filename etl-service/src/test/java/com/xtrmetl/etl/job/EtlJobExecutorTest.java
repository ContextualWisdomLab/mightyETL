package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlIdempotencyResult;
import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves internal idempotency and current-lease success updates share the executor boundary.
 */
class EtlJobExecutorTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void processesWithTheJobIdentifierAndMarksTheCurrentLeaseSuccessful() {
        EtlService etlService = mock(EtlService.class);
        EtlJobStore jobStore = mock(EtlJobStore.class);
        EtlJobExecutor executor = new EtlJobExecutor(etlService, jobStore);
        EtlJobClaim claim = claim();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(etlService.processDataIdempotently(
                claim.requestPayload(),
                claim.jobRecordId().toString(),
                claim.principalScopeHash()
        )).thenReturn(new EtlIdempotencyResult("Processed: record_alpha", false));

        executor.execute(claim);

        verify(jobStore).markSucceeded(
                claim.jobRecordId(),
                claim.leaseOwnerId(),
                "Processed: record_alpha"
        );
    }

    @Test
    void propagatesLostLeaseFailureSoTheTransactionCanRollBack() {
        EtlService etlService = mock(EtlService.class);
        EtlJobStore jobStore = mock(EtlJobStore.class);
        EtlJobExecutor executor = new EtlJobExecutor(etlService, jobStore);
        EtlJobClaim claim = claim();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(etlService.processDataIdempotently(
                claim.requestPayload(),
                claim.jobRecordId().toString(),
                claim.principalScopeHash()
        )).thenReturn(new EtlIdempotencyResult("Processed: record_alpha", false));
        doThrow(new IllegalStateException("lost lease"))
                .when(jobStore)
                .markSucceeded(
                        claim.jobRecordId(),
                        claim.leaseOwnerId(),
                        "Processed: record_alpha"
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(claim)
        );

        assertEquals("lost lease", exception.getMessage());
    }

    @Test
    void failsClosedBeforeProcessingWithoutAnActiveTransaction() {
        EtlService etlService = mock(EtlService.class);
        EtlJobStore jobStore = mock(EtlJobStore.class);
        EtlJobExecutor executor = new EtlJobExecutor(etlService, jobStore);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(claim())
        );

        assertEquals(
                "Durable ETL job execution requires an active transaction",
                exception.getMessage()
        );
        verifyNoInteractions(etlService, jobStore);
    }

    @Test
    void rejectsMissingCollaboratorsAndClaims() {
        EtlService etlService = mock(EtlService.class);
        EtlJobStore jobStore = mock(EtlJobStore.class);
        assertThrows(NullPointerException.class, () -> new EtlJobExecutor(null, jobStore));
        assertThrows(NullPointerException.class, () -> new EtlJobExecutor(etlService, null));
        EtlJobExecutor executor = new EtlJobExecutor(etlService, jobStore);
        assertThrows(NullPointerException.class, () -> executor.execute(null));
    }

    private static EtlJobClaim claim() {
        return new EtlJobClaim(
                UUID.fromString("7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20"),
                "a".repeat(64),
                "[{\"id\":\"record_alpha\"}]",
                1,
                "worker_alpha:lease_beta"
        );
    }
}
