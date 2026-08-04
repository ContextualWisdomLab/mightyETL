package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlIdempotencyKey;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.EtlService;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Validates, deduplicates, persists, and retrieves principal-scoped asynchronous ETL jobs.
 */
@Service
public class EtlJobService {

    private static final int MAX_PRINCIPAL_SCOPE_CODE_POINTS = 512;
    private static final String PRODUCT_SCOPE = "mightyetl";

    private final EtlService etlService;
    private final EtlRequestLock requestLock;
    private final EtlJobStore jobStore;

    /**
     * Creates the asynchronous ETL application service.
     *
     * @param etlService side-effect-free admission validation
     * @param requestLock transaction-lifetime nonblocking submission lock
     * @param jobStore durable job persistence
     */
    public EtlJobService(
            EtlService etlService,
            EtlRequestLock requestLock,
            EtlJobStore jobStore
    ) {
        this.etlService = Objects.requireNonNull(etlService, "etlService must not be null");
        this.requestLock = Objects.requireNonNull(requestLock, "requestLock must not be null");
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore must not be null");
    }

    /**
     * Creates or replays one validated principal-scoped durable ETL job.
     *
     * <p>Admission validation completes before lock or store access. The transaction advisory
     * try-lock and unique database constraint serialize same-owner/same-key submissions without
     * retaining the raw principal or client key.</p>
     *
     * @param requestPayload decoded JSON request text
     * @param idempotencyKey quoted RFC 9651 String or retained legacy raw key
     * @param principalName authenticated owner name
     * @return new or previously submitted owner-safe job representation
     * @throws EtlRequestException when validation, ownership, key, or payload contracts fail
     * @throws IllegalStateException when invoked without an active transaction
     */
    @Retryable(
            retryFor = TransientDataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public EtlJobView submit(
            @Nullable String requestPayload,
            @Nullable String idempotencyKey,
            @Nullable String principalName
    ) {
        String semanticKey = EtlIdempotencyKey.normalize(idempotencyKey);
        String validatedPrincipal = validatePrincipal(principalName);
        etlService.validateData(requestPayload);
        requireActiveTransaction();

        String principalScopeHash = EtlIdempotencyKey.domainScopedHash(
                "job_principal",
                PRODUCT_SCOPE,
                validatedPrincipal
        );
        String submissionKeyHash = EtlIdempotencyKey.domainScopedHash(
                "job_submission",
                principalScopeHash,
                semanticKey
        );
        String requestDigest = EtlIdempotencyKey.domainScopedHash(
                "job_payload",
                principalScopeHash,
                Objects.requireNonNull(requestPayload, "requestPayload must not be null")
        );
        String requestLockHash = EtlIdempotencyKey.domainScopedHash(
                "job_submission_lock",
                principalScopeHash,
                semanticKey
        );

        if (!requestLock.tryLock(requestLockHash)) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        EtlJobRecord existing = jobStore.findBySubmission(
                principalScopeHash,
                submissionKeyHash
        ).orElse(null);
        if (existing != null) {
            if (!existing.requestDigest().equals(requestDigest)) {
                throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_KEY_REUSED);
            }
            return existing.toView();
        }

        return jobStore.insertPending(
                UUID.randomUUID(),
                principalScopeHash,
                submissionKeyHash,
                requestDigest,
                requestPayload
        ).toView();
    }

    /**
     * Returns one job only when it belongs to the authenticated principal namespace.
     *
     * <p>Missing and foreign-owned identifiers use the same 404 classification so callers cannot
     * probe another principal's job inventory.</p>
     *
     * @param jobRecordId opaque job identifier
     * @param principalName authenticated owner name
     * @return owner-safe current representation
     * @throws EtlRequestException when the principal is absent or no owned job is found
     */
    public EtlJobView get(UUID jobRecordId, @Nullable String principalName) {
        UUID requiredJobId = Objects.requireNonNull(jobRecordId, "jobRecordId must not be null");
        String principalScopeHash = EtlIdempotencyKey.domainScopedHash(
                "job_principal",
                PRODUCT_SCOPE,
                validatePrincipal(principalName)
        );
        return jobStore.findOwned(requiredJobId, principalScopeHash)
                .map(EtlJobRecord::toView)
                .orElseThrow(() -> new EtlRequestException(EtlRequestError.JOB_NOT_FOUND));
    }

    private static String validatePrincipal(@Nullable String principalName) {
        if (principalName == null
                || principalName.isBlank()
                || principalName.codePointCount(0, principalName.length())
                > MAX_PRINCIPAL_SCOPE_CODE_POINTS) {
            throw new EtlRequestException(EtlRequestError.JOB_PRINCIPAL_REQUIRED);
        }
        return principalName;
    }

    private static void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Durable ETL job submission requires an active transaction"
            );
        }
    }
}
