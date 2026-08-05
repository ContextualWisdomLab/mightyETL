package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Specifies one-job polling, bounded retries, stable failures, stale fencing, and safe metrics.
 */
@ExtendWith(MockitoExtension.class)
class EtlJobWorkerTest {

    private static final String OWNER_ID = "worker-alpha";
    private static final String PRINCIPAL_SCOPE_HASH = "a".repeat(64);
    private static final String SUBMISSION_KEY_HASH = "b".repeat(64);
    private static final String REQUEST_DIGEST = "c".repeat(64);
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";

    @Mock
    private EtlJobLeaseRepository leaseRepository;

    @Mock
    private EtlJobExecutionService executionService;

    private EtlJobWorkerProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private EtlJobWorker worker;

    @BeforeEach
    void createWorker() {
        properties = new EtlJobWorkerProperties();
        properties.setEnabled(true);
        properties.setLeaseOwnerId(OWNER_ID);
        properties.setLeaseDurationSeconds(120L);
        properties.setMaxAttempts(3);
        meterRegistry = new SimpleMeterRegistry();
        worker = new EtlJobWorker(
                leaseRepository,
                executionService,
                properties,
                meterRegistry
        );
    }

    @Test
    void recordsIdleWithoutExecutingWhenNoJobIsEligible() {
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.empty());

        worker.pollOnce();

        verify(executionService, never()).execute(any());
        assertMetric("idle", 0.0, 1L);
    }

    @Test
    void executesAtMostOneClaimAndRecordsClaimedAndSucceeded() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));

        worker.pollOnce();

        verify(executionService).execute(lease);
        assertMetric("claimed", 1.0, 0L);
        assertMetric("succeeded", 1.0, 1L);
    }

    @Test
    void releasesTransientFailureWhenAttemptsRemain() {
        EtlJobLease lease = lease(2);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new CannotAcquireLockException("temporary"))
                .when(executionService).execute(lease);

        worker.pollOnce();

        verify(leaseRepository).releaseForRetry(lease, 3);
        verify(leaseRepository, never()).markFailed(any(), anyString());
        assertMetric("retried", 1.0, 1L);
    }

    @Test
    void terminalizesTransientFailureAtTheAttemptLimit() {
        EtlJobLease lease = lease(3);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new CannotAcquireLockException("temporary"))
                .when(executionService).execute(lease);

        worker.pollOnce();

        verify(leaseRepository).markFailed(lease, "etl_target_unavailable");
        verify(leaseRepository, never()).releaseForRetry(any(), anyInt());
        assertMetric("failed", 1.0, 1L);
    }

    @Test
    void terminalizesIntegrityFailureWithItsStableCode() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new EtlJobIntegrityException()).when(executionService).execute(lease);

        worker.pollOnce();

        verify(leaseRepository).markFailed(lease, "etl_job_integrity_failure");
        assertMetric("failed", 1.0, 1L);
    }

    @Test
    void terminalizesDeterministicRequestFailureWithItsStableCode() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new EtlRequestException(EtlRequestError.INVALID_JSON))
                .when(executionService).execute(lease);

        worker.pollOnce();

        verify(leaseRepository).markFailed(lease, "etl_invalid_json");
        assertMetric("failed", 1.0, 1L);
    }

    @Test
    void terminalizesNonTransientTargetFailureWithoutDiagnosticText() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new DataIntegrityViolationException("sensitive SQL"))
                .when(executionService).execute(lease);

        worker.pollOnce();

        verify(leaseRepository).markFailed(lease, "etl_target_failure");
        assertMetric("failed", 1.0, 1L);
    }

    @Test
    void terminalizesUnexpectedRuntimeFailureWithGenericCode() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new IllegalStateException("sensitive implementation detail"))
                .when(executionService).execute(lease);

        worker.pollOnce();

        verify(leaseRepository).markFailed(lease, "etl_internal_error");
        assertMetric("failed", 1.0, 1L);
    }

    @Test
    void treatsAnExecutionFenceFailureAsStaleEvidence() {
        EtlJobLease executionStaleLease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(executionStaleLease));
        doThrow(new StaleEtlJobLeaseException())
                .when(executionService).execute(executionStaleLease);

        worker.pollOnce();

        verify(leaseRepository, never()).markFailed(any(), anyString());
        verify(leaseRepository, never()).releaseForRetry(any(), anyInt());
        assertMetric("stale", 1.0, 1L);
    }

    @Test
    void treatsAStaleRetryTransitionAsStaleEvidence() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new CannotAcquireLockException("temporary"))
                .when(executionService).execute(lease);
        doThrow(new StaleEtlJobLeaseException())
                .when(leaseRepository).releaseForRetry(lease, 3);

        worker.pollOnce();

        assertMetric("stale", 1.0, 1L);
        assertMetric("retried", 0.0, 0L);
    }

    @Test
    void treatsAStaleTerminalTransitionAsStaleEvidence() {
        EtlJobLease lease = lease(1);
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenReturn(Optional.of(lease));
        doThrow(new EtlRequestException(EtlRequestError.INVALID_JSON))
                .when(executionService).execute(lease);
        doThrow(new StaleEtlJobLeaseException())
                .when(leaseRepository).markFailed(lease, "etl_invalid_json");

        worker.pollOnce();

        assertMetric("stale", 1.0, 1L);
        assertMetric("failed", 0.0, 0L);
    }

    @Test
    void recordsClaimDatabaseFailureWithoutLeakingOrExecuting() {
        when(leaseRepository.claimNext(anyString(), any(), anyInt()))
                .thenThrow(new CannotAcquireLockException("sensitive SQL"));

        worker.pollOnce();

        verify(executionService, never()).execute(any());
        assertMetric("failed", 1.0, 1L);
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobWorker(null, executionService, properties, meterRegistry)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobWorker(leaseRepository, null, properties, meterRegistry)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobWorker(leaseRepository, executionService, null, meterRegistry)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobWorker(leaseRepository, executionService, properties, null)
        );
    }

    private void assertMetric(String outcome, double counterCount, long timerCount) {
        assertEquals(
                counterCount,
                meterRegistry.find("etl.jobs.worker.outcomes")
                        .tag("outcome", outcome)
                        .counter()
                        .count()
        );
        assertEquals(
                timerCount,
                meterRegistry.find("etl.jobs.execution.duration")
                        .tag("outcome", outcome)
                        .timer()
                        .count()
        );
    }

    private static EtlJobLease lease(int attemptCount) {
        return new EtlJobLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OWNER_ID,
                PRINCIPAL_SCOPE_HASH,
                SUBMISSION_KEY_HASH,
                REQUEST_DIGEST,
                PAYLOAD,
                attemptCount,
                Instant.now().plusSeconds(300)
        );
    }
}
