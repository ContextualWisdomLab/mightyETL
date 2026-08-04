package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wakes on a fixed delay, claims at most one durable job, and applies bounded retry policy.
 *
 * <p>PostgreSQL owns cross-instance distribution. The scheduler is only a wake-up mechanism, so
 * multiple service replicas or scheduler threads cannot claim the same locked row.</p>
 */
@Component
public class EtlJobWorker {

    private static final Logger log = LoggerFactory.getLogger(EtlJobWorker.class);

    private final EtlJobStore jobStore;
    private final EtlJobExecutor jobExecutor;
    private final EtlJobProperties properties;
    private final String processWorkerId;

    /**
     * Creates one scheduled worker with an opaque process identifier.
     *
     * @param jobStore durable claim and state persistence
     * @param jobExecutor transactional target executor
     * @param properties bounded worker settings
     */
    public EtlJobWorker(
            EtlJobStore jobStore,
            EtlJobExecutor jobExecutor,
            EtlJobProperties properties
    ) {
        this(jobStore, jobExecutor, properties, UUID.randomUUID().toString());
    }

    EtlJobWorker(
            EtlJobStore jobStore,
            EtlJobExecutor jobExecutor,
            EtlJobProperties properties,
            String processWorkerId
    ) {
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore must not be null");
        this.jobExecutor = Objects.requireNonNull(jobExecutor, "jobExecutor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.processWorkerId = Objects.requireNonNull(
                processWorkerId,
                "processWorkerId must not be null"
        );
    }

    /**
     * Claims and processes at most one eligible job after each configured fixed delay.
     */
    @Scheduled(
            fixedDelayString = "${xtrmetl.etl.jobs.poll-delay-ms:1000}",
            initialDelayString = "${xtrmetl.etl.jobs.poll-delay-ms:1000}"
    )
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }

        String leaseOwnerId = processWorkerId + ":" + UUID.randomUUID();
        Optional<EtlJobClaim> optionalClaim = jobStore.claimNext(
                leaseOwnerId,
                properties.getLeaseDurationSeconds()
        );
        if (optionalClaim.isEmpty()) {
            return;
        }

        execute(optionalClaim.get());
    }

    private void execute(EtlJobClaim claim) {
        try {
            jobExecutor.execute(claim);
        } catch (EtlRequestException exception) {
            if (exception.error() == EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS
                    && claim.attemptCount() < properties.getMaxAttempts()) {
                release(claim, exception.error().errorCode());
                return;
            }
            fail(claim, exception.error().errorCode());
        } catch (TransientDataAccessException exception) {
            if (claim.attemptCount() < properties.getMaxAttempts()) {
                release(claim, "etl_target_unavailable");
                return;
            }
            fail(claim, "etl_target_unavailable");
        } catch (DataAccessException exception) {
            fail(claim, "etl_target_failure");
        } catch (RuntimeException exception) {
            fail(claim, "etl_internal_error");
        }
    }

    private void release(EtlJobClaim claim, String reasonCode) {
        log.info(
                "Releasing durable ETL job for retry jobRecordId={} code={} attemptCount={}",
                claim.jobRecordId(),
                reasonCode,
                claim.attemptCount()
        );
        jobStore.releaseForRetry(claim.jobRecordId(), claim.leaseOwnerId());
    }

    private void fail(EtlJobClaim claim, String failureCode) {
        log.warn(
                "Failing durable ETL job jobRecordId={} code={} attemptCount={}",
                claim.jobRecordId(),
                failureCode,
                claim.attemptCount()
        );
        jobStore.markFailed(claim.jobRecordId(), claim.leaseOwnerId(), failureCode);
    }
}
