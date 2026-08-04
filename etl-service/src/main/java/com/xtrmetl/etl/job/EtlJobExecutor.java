package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlIdempotencyResult;
import com.xtrmetl.etl.service.EtlService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Executes one claimed job and commits target effects with terminal job success atomically.
 */
@Service
public class EtlJobExecutor {

    private final EtlService etlService;
    private final EtlJobStore jobStore;

    /**
     * Creates the transactional durable-job executor.
     *
     * @param etlService idempotent transactional ETL processor
     * @param jobStore terminal durable job persistence
     */
    public EtlJobExecutor(EtlService etlService, EtlJobStore jobStore) {
        this.etlService = Objects.requireNonNull(etlService, "etlService must not be null");
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore must not be null");
    }

    /**
     * Processes one lease claim and marks success inside the same Spring transaction.
     *
     * <p>The job UUID is a stable internal idempotency key and the stored owner hash isolates its
     * ledger namespace. A stale lease-owner update fails the transaction, rolling back both target
     * rows and the internal response-ledger insert.</p>
     *
     * @param claim complete current lease claim
     * @throws IllegalStateException when no transaction is active or the lease was lost
     */
    @Transactional
    public void execute(EtlJobClaim claim) {
        EtlJobClaim requiredClaim = Objects.requireNonNull(claim, "claim must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Durable ETL job execution requires an active transaction");
        }
        EtlIdempotencyResult result = etlService.processDataIdempotently(
                requiredClaim.requestPayload(),
                requiredClaim.jobRecordId().toString(),
                requiredClaim.principalScopeHash()
        );
        jobStore.markSucceeded(
                requiredClaim.jobRecordId(),
                requiredClaim.leaseOwnerId(),
                result.responseBody()
        );
    }
}
