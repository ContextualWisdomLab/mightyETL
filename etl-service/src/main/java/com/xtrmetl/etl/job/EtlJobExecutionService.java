package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Executes one claimed ETL payload and commits terminal success in the same transaction.
 *
 * <p>The existing {@link EtlService} performs validated target writes. The subsequent conditional
 * success transition must match the exact unexpired claim. If that transition reports a stale
 * lease, {@link StaleEtlJobLeaseException} escapes and Spring rolls back every target write made by
 * the same transaction.</p>
 */
@Service
public class EtlJobExecutionService {

    private final EtlService etlService;
    private final EtlJobLeaseRepository leaseRepository;

    /**
     * Creates the atomic durable-job execution boundary.
     *
     * @param etlService validated ETL target writer
     * @param leaseRepository exact lease-fenced lifecycle persistence
     */
    public EtlJobExecutionService(
            EtlService etlService,
            EtlJobLeaseRepository leaseRepository
    ) {
        this.etlService = Objects.requireNonNull(etlService, "etlService must not be null");
        this.leaseRepository = Objects.requireNonNull(
                leaseRepository,
                "leaseRepository must not be null"
        );
    }

    /**
     * Processes the retained payload and marks the exact live claim successful atomically.
     *
     * @param lease exact database claim to execute
     * @throws NullPointerException when the lease is {@code null}
     * @throws com.xtrmetl.etl.service.EtlRequestException when the retained request is invalid
     * @throws org.springframework.dao.DataAccessException when a target write fails
     * @throws StaleEtlJobLeaseException when the claim expires or is superseded before success
     */
    @Transactional
    public void execute(EtlJobLease lease) {
        EtlJobLease requiredLease = Objects.requireNonNull(lease, "lease must not be null");
        etlService.processData(requiredLease.requestPayload());
        leaseRepository.markSucceeded(requiredLease);
    }
}
