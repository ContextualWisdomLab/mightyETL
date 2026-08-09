package com.xtrmetl.etl.job;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Executes one claimed ETL payload and commits ledger, target, and terminal success atomically.
 *
 * <p>{@link EtlJobIdempotencyService} verifies the retained payload identity, acquires the durable
 * execution-ledger lock, replays or writes the response ledger, and writes target rows. The
 * subsequent conditional success transition must match the exact unexpired claim. If that
 * transition reports a stale lease, {@link StaleEtlJobLeaseException} escapes and Spring rolls back
 * every target and response-ledger write made by the same transaction.</p>
 */
@Service
public class EtlJobExecutionService {

    private final EtlJobIdempotencyService idempotencyService;
    private final EtlJobLeaseRepository leaseRepository;

    /**
     * Creates the atomic durable-job execution boundary.
     *
     * @param idempotencyService hashed response-ledger and target execution service
     * @param leaseRepository exact lease-fenced lifecycle persistence
     */
    public EtlJobExecutionService(
            EtlJobIdempotencyService idempotencyService,
            EtlJobLeaseRepository leaseRepository
    ) {
        this.idempotencyService = Objects.requireNonNull(
                idempotencyService,
                "idempotencyService must not be null"
        );
        this.leaseRepository = Objects.requireNonNull(
                leaseRepository,
                "leaseRepository must not be null"
        );
    }

    /**
     * Processes or replays the retained job and marks the exact live claim successful atomically.
     *
     * @param lease exact database claim to execute
     * @throws NullPointerException when the lease is {@code null}
     * @throws EtlJobIntegrityException when persisted job or ledger identity conflicts
     * @throws com.xtrmetl.etl.service.EtlRequestException when the retained request is invalid
     * @throws org.springframework.dao.DataAccessException when locking or a database write fails
     * @throws StaleEtlJobLeaseException when the claim expires or is superseded before success
     */
    @Transactional
    public void execute(EtlJobLease lease) {
        EtlJobLease requiredLease = Objects.requireNonNull(lease, "lease must not be null");
        idempotencyService.process(requiredLease);
        leaseRepository.markSucceeded(requiredLease);
    }
}
