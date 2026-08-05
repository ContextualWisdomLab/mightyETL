package com.xtrmetl.etl.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Holds bounded, fail-closed configuration for durable ETL job execution.
 *
 * <p>The worker is disabled unless an operator explicitly enables it. Polling delays and lease
 * durations are capped at one day so a malformed environment value cannot create effectively
 * permanent scheduling gaps, arithmetic overflow, or a lease that prevents timely crash recovery.
 * A process-lifetime lease owner identifier is generated when no external value is supplied. The
 * identifier is deliberately restricted to a short safe ASCII profile because it is persisted as
 * operational metadata and must never become a free-form log or database injection surface.</p>
 */
@ConfigurationProperties(prefix = "xtrmetl.etl.jobs.worker")
public class EtlJobWorkerProperties {

    /** Maximum supported fixed or initial scheduler delay: one day in milliseconds. */
    public static final long MAXIMUM_SCHEDULER_DELAY_MILLISECONDS = 86_400_000L;

    /** Maximum supported durable-job lease duration: one day in seconds. */
    public static final long MAXIMUM_LEASE_DURATION_SECONDS = 86_400L;

    private static final Pattern SAFE_LEASE_OWNER_PATTERN = Pattern.compile(
            "[A-Za-z0-9._:-]{8,128}"
    );

    private boolean enabled;
    private long fixedDelayMilliseconds = 5_000L;
    private long initialDelayMilliseconds = 5_000L;
    private long leaseDurationSeconds = 300L;
    private int maxAttempts = 3;
    private String leaseOwnerId = "worker-" + UUID.randomUUID();

    /**
     * Creates disabled worker configuration with bounded production-safe defaults.
     */
    public EtlJobWorkerProperties() {
        // Spring Boot binds through the public setters while preserving generated defaults.
    }

    /**
     * Reports whether scheduled durable-job execution is explicitly enabled.
     *
     * @return {@code true} only when an operator enabled the worker
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables scheduled durable-job execution.
     *
     * @param enabled whether the worker should run
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the delay measured after one polling invocation completes.
     *
     * @return fixed delay from one millisecond through one day
     */
    public long getFixedDelayMilliseconds() {
        return fixedDelayMilliseconds;
    }

    /**
     * Sets the delay measured after one polling invocation completes.
     *
     * @param fixedDelayMilliseconds delay from one millisecond through one day
     * @throws IllegalArgumentException when the delay is outside the supported range
     */
    public void setFixedDelayMilliseconds(long fixedDelayMilliseconds) {
        if (fixedDelayMilliseconds < 1L
                || fixedDelayMilliseconds > MAXIMUM_SCHEDULER_DELAY_MILLISECONDS) {
            throw new IllegalArgumentException(
                    "fixedDelayMilliseconds must be between 1 and "
                            + MAXIMUM_SCHEDULER_DELAY_MILLISECONDS
            );
        }
        this.fixedDelayMilliseconds = fixedDelayMilliseconds;
    }

    /**
     * Returns the delay before the first polling invocation after application startup.
     *
     * @return initial delay from zero milliseconds through one day
     */
    public long getInitialDelayMilliseconds() {
        return initialDelayMilliseconds;
    }

    /**
     * Sets the delay before the first polling invocation after application startup.
     *
     * @param initialDelayMilliseconds delay from zero milliseconds through one day
     * @throws IllegalArgumentException when the delay is outside the supported range
     */
    public void setInitialDelayMilliseconds(long initialDelayMilliseconds) {
        if (initialDelayMilliseconds < 0L
                || initialDelayMilliseconds > MAXIMUM_SCHEDULER_DELAY_MILLISECONDS) {
            throw new IllegalArgumentException(
                    "initialDelayMilliseconds must be between 0 and "
                            + MAXIMUM_SCHEDULER_DELAY_MILLISECONDS
            );
        }
        this.initialDelayMilliseconds = initialDelayMilliseconds;
    }

    /**
     * Returns how long one database claim remains valid without renewal.
     *
     * @return lease duration from one second through one day
     */
    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    /**
     * Sets how long one database claim remains valid without renewal.
     *
     * @param leaseDurationSeconds duration from one second through one day
     * @throws IllegalArgumentException when the duration is outside the supported range
     */
    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        if (leaseDurationSeconds < 1L
                || leaseDurationSeconds > MAXIMUM_LEASE_DURATION_SECONDS) {
            throw new IllegalArgumentException(
                    "leaseDurationSeconds must be between 1 and "
                            + MAXIMUM_LEASE_DURATION_SECONDS
            );
        }
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    /**
     * Returns the maximum number of claims permitted before terminal failure.
     *
     * @return maximum attempt count from 1 through 100
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Sets the maximum number of claims permitted before terminal failure.
     *
     * @param maxAttempts maximum attempt count from 1 through 100
     * @throws IllegalArgumentException when the value is outside the supported range
     */
    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
        }
        this.maxAttempts = maxAttempts;
    }

    /**
     * Returns the non-sensitive process identifier persisted on active leases.
     *
     * @return safe process-lifetime lease owner identifier
     */
    public String getLeaseOwnerId() {
        return leaseOwnerId;
    }

    /**
     * Sets the non-sensitive process identifier persisted on active leases.
     *
     * @param leaseOwnerId 8-to-128-character safe ASCII process identifier
     * @throws NullPointerException when the identifier is {@code null}
     * @throws IllegalArgumentException when the identifier is too short, too long, or unsafe
     */
    public void setLeaseOwnerId(String leaseOwnerId) {
        String requiredOwnerId = Objects.requireNonNull(
                leaseOwnerId,
                "leaseOwnerId must not be null"
        );
        if (!SAFE_LEASE_OWNER_PATTERN.matcher(requiredOwnerId).matches()) {
            throw new IllegalArgumentException(
                    "leaseOwnerId must match [A-Za-z0-9._:-]{8,128}"
            );
        }
        this.leaseOwnerId = requiredOwnerId;
    }
}
