package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlIdempotencyKey;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves validation, durable submission replay, conflict, transaction, and owner-isolation rules.
 */
class EtlJobServiceTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String PRINCIPAL = "tenant_alpha";
    private static final Instant NOW = Instant.parse("2026-08-04T10:30:00Z");

    private EtlService etlService;
    private EtlRequestLock requestLock;
    private EtlJobStore jobStore;
    private EtlJobService jobService;

    @BeforeEach
    void setUp() {
        etlService = mock(EtlService.class);
        requestLock = mock(EtlRequestLock.class);
        jobStore = mock(EtlJobStore.class);
        jobService = new EtlJobService(etlService, requestLock, jobStore);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void validatesBeforeLockOrStoreAccess() {
        EtlRequestException rejection = new EtlRequestException(EtlRequestError.INVALID_RECORD);
        doThrow(rejection).when(etlService).validateData(PAYLOAD);

        EtlRequestException actual = assertThrows(
                EtlRequestException.class,
                () -> jobService.submit(PAYLOAD, KEY, PRINCIPAL)
        );

        assertEquals(EtlRequestError.INVALID_RECORD, actual.error());
        verifyNoInteractions(requestLock, jobStore);
    }

    @Test
    void insertsAValidatedPendingJobWithOnlyHashedOwnerAndKeyData() {
        when(requestLock.tryLock(anyString())).thenReturn(true);
        when(jobStore.findBySubmission(anyString(), anyString())).thenReturn(Optional.empty());
        when(jobStore.insertPending(
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                eq(PAYLOAD)
        )).thenAnswer(invocation -> pendingRecord(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3)
        ));

        EtlJobView view = jobService.submit(PAYLOAD, "\"" + KEY + "\"", PRINCIPAL);

        assertEquals(EtlJobStatus.PENDING, view.jobStatus());
        assertTrue(view.statusUrl().endsWith(view.jobRecordId().toString()));
        verify(etlService).validateData(PAYLOAD);
        ArgumentCaptor<String> lockHash = ArgumentCaptor.forClass(String.class);
        verify(requestLock).tryLock(lockHash.capture());
        assertEquals(64, lockHash.getValue().length());
        verify(jobStore).insertPending(
                eq(view.jobRecordId()),
                anyString(),
                anyString(),
                anyString(),
                eq(PAYLOAD)
        );
    }

    @Test
    void replaysTheExistingJobForTheSameSemanticKeyAndPayload() {
        String principalHash = principalHash(PRINCIPAL);
        String submissionHash = submissionHash(principalHash, KEY);
        String requestDigest = requestDigest(principalHash, PAYLOAD);
        EtlJobRecord existing = pendingRecord(
                UUID.fromString("7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20"),
                principalHash,
                submissionHash,
                requestDigest
        );
        when(requestLock.tryLock(anyString())).thenReturn(true);
        when(jobStore.findBySubmission(principalHash, submissionHash))
                .thenReturn(Optional.of(existing));

        EtlJobView replay = jobService.submit(PAYLOAD, KEY, PRINCIPAL);

        assertEquals(existing.jobRecordId(), replay.jobRecordId());
        verify(jobStore, never()).insertPending(
                any(UUID.class), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void rejectsReuseOfTheSameSubmissionKeyWithDifferentJsonText() {
        String principalHash = principalHash(PRINCIPAL);
        String submissionHash = submissionHash(principalHash, KEY);
        EtlJobRecord existing = pendingRecord(
                UUID.randomUUID(),
                principalHash,
                submissionHash,
                requestDigest(principalHash, "[{\"id\":\"record_beta\"}]")
        );
        when(requestLock.tryLock(anyString())).thenReturn(true);
        when(jobStore.findBySubmission(principalHash, submissionHash))
                .thenReturn(Optional.of(existing));

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> jobService.submit(PAYLOAD, KEY, PRINCIPAL)
        );

        assertEquals(EtlRequestError.IDEMPOTENCY_KEY_REUSED, exception.error());
        verify(jobStore, never()).insertPending(
                any(UUID.class), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void rejectsAConcurrentSubmissionBeforeStoreLookup() {
        when(requestLock.tryLock(anyString())).thenReturn(false);

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> jobService.submit(PAYLOAD, KEY, PRINCIPAL)
        );

        assertEquals(EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS, exception.error());
        verifyNoInteractions(jobStore);
    }

    @Test
    void failsClosedWithoutAnActiveSubmissionTransaction() {
        TransactionSynchronizationManager.clear();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> jobService.submit(PAYLOAD, KEY, PRINCIPAL)
        );

        assertEquals(
                "Durable ETL job submission requires an active transaction",
                exception.getMessage()
        );
        verify(etlService).validateData(PAYLOAD);
        verifyNoInteractions(requestLock, jobStore);
    }

    @Test
    void returnsOnlyAnOwnedJobAndHidesMissingOrForeignIdentifiers() {
        UUID jobId = UUID.fromString("7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20");
        String principalHash = principalHash(PRINCIPAL);
        EtlJobRecord owned = pendingRecord(
                jobId,
                principalHash,
                submissionHash(principalHash, KEY),
                requestDigest(principalHash, PAYLOAD)
        );
        when(jobStore.findOwned(jobId, principalHash)).thenReturn(Optional.of(owned));

        assertEquals(jobId, jobService.get(jobId, PRINCIPAL).jobRecordId());

        UUID missing = UUID.randomUUID();
        when(jobStore.findOwned(missing, principalHash)).thenReturn(Optional.empty());
        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> jobService.get(missing, PRINCIPAL)
        );
        assertEquals(EtlRequestError.JOB_NOT_FOUND, exception.error());
    }

    @Test
    void rejectsMissingOrOversizedPrincipalNamesBeforeHashOrStoreWork() {
        for (String principal : new String[]{null, " ", "x".repeat(513)}) {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> jobService.submit(PAYLOAD, KEY, principal)
            );
            assertEquals(EtlRequestError.JOB_PRINCIPAL_REQUIRED, exception.error());
        }
        verifyNoInteractions(requestLock, jobStore);
    }

    private static String principalHash(String principal) {
        return EtlIdempotencyKey.domainScopedHash("job_principal", "mightyetl", principal);
    }

    private static String submissionHash(String principalHash, String key) {
        return EtlIdempotencyKey.domainScopedHash("job_submission", principalHash, key);
    }

    private static String requestDigest(String principalHash, String payload) {
        return EtlIdempotencyKey.domainScopedHash("job_payload", principalHash, payload);
    }

    private static EtlJobRecord pendingRecord(
            UUID jobId,
            String principalHash,
            String submissionHash,
            String requestDigest
    ) {
        return new EtlJobRecord(
                jobId,
                principalHash,
                submissionHash,
                requestDigest,
                PAYLOAD,
                EtlJobStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                NOW,
                null,
                null,
                NOW
        );
    }
}
