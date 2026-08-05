package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlRequestException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Polls and executes at most one durable ETL job per fixed-delay invocation.
 *
 * <p>The database claim repository, not the scheduler, distributes work across replicas. The
 * worker classifies failures into stable non-sensitive codes, retries only transient database
 * failures while attempts remain, and treats every failed exact-lease transition as stale evidence.
 * Metrics use a fixed outcome vocabulary and never tag payloads, principals, keys, job identifiers,
 * lease identifiers, SQL, exception classes, or exception messages. Every completed poll records
 * one terminal outcome counter and one matching duration sample, including idle polls and database
 * failures while persisting retry or terminal transitions.</p>
 */
@Component
@ConditionalOnBooleanProperty(
        prefix = "xtrmetl.etl.jobs.worker",
        name = "enabled",
        havingValue = true,
        matchIfMissing = false
)
public class EtlJobWorker {

    /** Stable target-unavailability code used after transient attempts are exhausted. */
    public static final String TARGET_UNAVAILABLE_FAILURE_CODE = "etl_target_unavailable";

    /** Stable non-transient database failure code. */
    public static final String TARGET_FAILURE_CODE = "etl_target_failure";

    /** Stable unexpected implementation failure code. */
    public static final String INTERNAL_FAILURE_CODE = "etl_internal_error";

    private static final String METRIC_OUTCOMES = "etl.jobs.worker.outcomes";
    private static final String METRIC_DURATION = "etl.jobs.execution.duration";
    private static final String IDLE_OUTCOME = "idle";
    private static final String CLAIMED_OUTCOME = "claimed";
    private static final String SUCCEEDED_OUTCOME = "succeeded";
    private static final String RETRIED_OUTCOME = "retried";
    private static final String FAILED_OUTCOME = "failed";
    private static final String STALE_OUTCOME = "stale";
    private static final List<String> FINITE_OUTCOMES = List.of(
            IDLE_OUTCOME,
            CLAIMED_OUTCOME,
            SUCCEEDED_OUTCOME,
            RETRIED_OUTCOME,
            FAILED_OUTCOME,
            STALE_OUTCOME
    );

    private final EtlJobLeaseRepository leaseRepository;
    private final EtlJobExecutionService executionService;
    private final EtlJobWorkerProperties properties;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> outcomeCounters;
    private final Map<String, Timer> outcomeTimers;

    /**
     * Creates one fail-closed worker and pre-registers its finite metric vocabulary.
     *
     * @param leaseRepository database claim and transition authority
     * @param executionService atomic target-write and success boundary
     * @param properties bounded worker configuration
     * @param meterRegistry metrics registry for finite-cardinality evidence
     */
    public EtlJobWorker(
            EtlJobLeaseRepository leaseRepository,
            EtlJobExecutionService executionService,
            EtlJobWorkerProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.leaseRepository = Objects.requireNonNull(
                leaseRepository,
                "leaseRepository must not be null"
        );
        this.executionService = Objects.requireNonNull(
                executionService,
                "executionService must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
        );

        Map<String, Counter> counters = new LinkedHashMap<>();
        Map<String, Timer> timers = new LinkedHashMap<>();
        for (String outcome : FINITE_OUTCOMES) {
            counters.put(
                    outcome,
                    Counter.builder(METRIC_OUTCOMES)
                            .description("Durable ETL worker outcomes")
                            .tag("outcome", outcome)
                            .register(this.meterRegistry)
            );
            timers.put(
                    outcome,
                    Timer.builder(METRIC_DURATION)
                            .description("Duration of one durable ETL worker poll")
                            .tag("outcome", outcome)
                            .register(this.meterRegistry)
            );
        }
        this.outcomeCounters = Map.copyOf(counters);
        this.outcomeTimers = Map.copyOf(timers);
    }

    /**
     * Claims and handles at most one eligible durable job.
     *
     * <p>Fixed delay is measured after this invocation completes. A database outage during claim or
     * a database failure while persisting retry or terminal state is converted into a finite failed
     * outcome without copying diagnostic text into application logs or telemetry. Spring invokes the
     * method only when worker activation is explicitly enabled.</p>
     */
    @Scheduled(
            fixedDelayString = "${xtrmetl.etl.jobs.worker.fixed-delay-milliseconds:5000}",
            initialDelayString = "${xtrmetl.etl.jobs.worker.initial-delay-milliseconds:5000}"
    )
    public void pollOnce() {
        Timer.Sample sample = Timer.start(meterRegistry);
        String finalOutcome = runOnePoll();
        sample.stop(outcomeTimers.get(finalOutcome));
    }

    private String runOnePoll() {
        final Optional<EtlJobLease> claimedLease;
        try {
            claimedLease = leaseRepository.claimNext(
                    properties.getLeaseOwnerId(),
                    Duration.ofSeconds(properties.getLeaseDurationSeconds()),
                    properties.getMaxAttempts()
            );
        } catch (DataAccessException exception) {
            increment(FAILED_OUTCOME);
            return FAILED_OUTCOME;
        }

        if (claimedLease.isEmpty()) {
            increment(IDLE_OUTCOME);
            return IDLE_OUTCOME;
        }

        EtlJobLease lease = claimedLease.orElseThrow();
        increment(CLAIMED_OUTCOME);
        try {
            executionService.execute(lease);
            increment(SUCCEEDED_OUTCOME);
            return SUCCEEDED_OUTCOME;
        } catch (StaleEtlJobLeaseException exception) {
            increment(STALE_OUTCOME);
            return STALE_OUTCOME;
        } catch (TransientDataAccessException exception) {
            return handleTransientFailure(lease);
        } catch (EtlJobIntegrityException exception) {
            return markFailedOrStale(lease, exception.failureCode());
        } catch (EtlRequestException exception) {
            return markFailedOrStale(lease, exception.error().errorCode());
        } catch (DataAccessException exception) {
            return markFailedOrStale(lease, TARGET_FAILURE_CODE);
        } catch (RuntimeException exception) {
            return markFailedOrStale(lease, INTERNAL_FAILURE_CODE);
        }
    }

    private String handleTransientFailure(EtlJobLease lease) {
        if (lease.attemptCount() < properties.getMaxAttempts()) {
            try {
                leaseRepository.releaseForRetry(lease, properties.getMaxAttempts());
                increment(RETRIED_OUTCOME);
                return RETRIED_OUTCOME;
            } catch (StaleEtlJobLeaseException exception) {
                increment(STALE_OUTCOME);
                return STALE_OUTCOME;
            } catch (DataAccessException exception) {
                increment(FAILED_OUTCOME);
                return FAILED_OUTCOME;
            }
        }
        return markFailedOrStale(lease, TARGET_UNAVAILABLE_FAILURE_CODE);
    }

    private String markFailedOrStale(EtlJobLease lease, String failureCode) {
        try {
            leaseRepository.markFailed(lease, failureCode);
            increment(FAILED_OUTCOME);
            return FAILED_OUTCOME;
        } catch (StaleEtlJobLeaseException exception) {
            increment(STALE_OUTCOME);
            return STALE_OUTCOME;
        } catch (DataAccessException exception) {
            increment(FAILED_OUTCOME);
            return FAILED_OUTCOME;
        }
    }

    private void increment(String outcome) {
        outcomeCounters.get(outcome).increment();
    }
}
