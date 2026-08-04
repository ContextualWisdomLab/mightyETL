package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers bounded polling and stable non-sensitive worker failure classification.
 */
class EtlJobWorkerTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final UUID LEASE_TOKEN = UUID.fromString(
            "72ab52a5-b060-4a20-af99-e701722bb221"
    );
    private static final Instant CLAIMED_AT = Instant.parse("2026-08-04T12:00:00Z");
    private static final EtlJobClaim CLAIM = new EtlJobClaim(
            JOB_RECORD_ID,
            LEASE_TOKEN,
            "[{\"id\":\"record_alpha\"}]",
            1,
            CLAIMED_AT,
            CLAIMED_AT.plusSeconds(300)
    );

    private EtlJobStore jobStore;
    private EtlJobExecutionService executionService;
    private EtlJobWorkerProperties properties;
    private EtlJobWorker worker;

    @BeforeEach
    void setUp() {
        jobStore = mock(EtlJobStore.class);
        executionService = mock(EtlJobExecutionService.class);
        properties = new EtlJobWorkerProperties();
        worker = new EtlJobWorker(jobStore, executionService, properties);
    }

    @Test
    void reportsAnEmptyQueueWithoutExecutingWork() {
        when(jobStore.claimNext()).thenReturn(null);

        assertFalse(worker.runOnce());

        verify(jobStore).claimNext();
        verifyNoInteractions(executionService);
    }

    @Test
    void executesOneClaimWithoutRecordingFailure() {
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenReturn(1);

        assertTrue(worker.runOnce());

        verify(executionService).execute(CLAIM);
        verify(jobStore, never()).recordFailure(CLAIM, "etl_internal_error", false);
    }

    @Test
    void doesNotOverwriteStateAfterLeaseLoss() {
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenThrow(
                new EtlJobLeaseLostException(JOB_RECORD_ID)
        );

        assertTrue(worker.runOnce());

        verify(jobStore, never()).recordFailure(
                CLAIM,
                "etl_internal_error",
                false
        );
    }

    @Test
    void requeuesTransientTargetFailureWithAStableCode() {
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenThrow(
                new TransientDataAccessResourceException("secret target detail")
        );

        assertTrue(worker.runOnce());

        verify(jobStore).recordFailure(CLAIM, "etl_target_unavailable", true);
    }

    @Test
    void terminatesNonTransientTargetFailureWithAStableCode() {
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenThrow(
                new DataIntegrityViolationException("secret constraint detail")
        );

        assertTrue(worker.runOnce());

        verify(jobStore).recordFailure(CLAIM, "etl_target_failure", false);
    }

    @Test
    void terminatesDeterministicRequestFailureWithItsExistingStableCode() {
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenThrow(
                new EtlRequestException(EtlRequestError.INVALID_RECORD)
        );

        assertTrue(worker.runOnce());

        verify(jobStore).recordFailure(CLAIM, "etl_invalid_record", false);
    }

    @Test
    void terminatesUnexpectedRuntimeFailureWithoutPersistingItsMessage() {
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenThrow(
                new IllegalStateException("secret runtime detail")
        );

        assertTrue(worker.runOnce());

        verify(jobStore).recordFailure(CLAIM, "etl_internal_error", false);
    }

    @Test
    void pollsSequentiallyUpToTheConfiguredBoundAndStopsAtTheFirstEmptyQueue() {
        EtlJobClaim second = new EtlJobClaim(
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                "[{\"id\":\"record_beta\"}]",
                1,
                CLAIMED_AT,
                CLAIMED_AT.plusSeconds(300)
        );
        properties.setJobsPerPoll(3);
        when(jobStore.claimNext()).thenReturn(CLAIM, second, null);
        when(executionService.execute(CLAIM)).thenReturn(1);
        when(executionService.execute(second)).thenReturn(1);

        worker.poll();

        verify(jobStore, times(3)).claimNext();
        verify(executionService).execute(CLAIM);
        verify(executionService).execute(second);
    }

    @Test
    void pollsExactlyTheConfiguredNumberWhenWorkRemainsAvailable() {
        properties.setJobsPerPoll(2);
        when(jobStore.claimNext()).thenReturn(CLAIM);
        when(executionService.execute(CLAIM)).thenReturn(1);

        worker.poll();

        verify(jobStore, times(2)).claimNext();
        verify(executionService, times(2)).execute(CLAIM);
    }
}
