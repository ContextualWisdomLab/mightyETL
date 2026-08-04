package com.xtrmetl.etl.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounded runtime configuration for PostgreSQL-backed asynchronous ETL jobs.
 */
@ConfigurationProperties(prefix = "xtrmetl.etl.jobs")
public class EtlJobProperties {

    /** Minimum fixed delay between worker polls. */
    public static final int MIN_POLL_DELAY_MS = 100;

    /** Maximum fixed delay between worker polls. */
    public static final int MAX_POLL_DELAY_MS = 60_000;

    /** Minimum lease duration. */
    public static final int MIN_LEASE_DURATION_SECONDS = 30;

    /** Maximum lease duration. */
    public static final int MAX_LEASE_DURATION_SECONDS = 86_400;

    /** Maximum supported execution attempts. */
    public static final int MAX_MAX_ATTEMPTS = 100;

    private boolean enabled = true;
    private int pollDelayMs = 1_000;
    private int leaseDurationSeconds = 300;
    private int maxAttempts = 5;

    /**
     * Returns whether a scheduled worker may claim jobs.
     *
     * @return {@code true} when execution is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables worker database access.
     *
     * @param enabled desired execution state
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the fixed delay after one poll completes.
     *
     * @return milliseconds between {@link #MIN_POLL_DELAY_MS} and {@link #MAX_POLL_DELAY_MS}
     */
    public int getPollDelayMs() {
        return pollDelayMs;
    }

    /**
     * Sets the fixed delay after one poll completes.
     *
     * @param pollDelayMs supported delay in milliseconds
     * @throws IllegalArgumentException when the delay is outside the supported range
     */
    public void setPollDelayMs(int pollDelayMs) {
        this.pollDelayMs = requireRange(
                "poll-delay-ms",
                pollDelayMs,
                MIN_POLL_DELAY_MS,
                MAX_POLL_DELAY_MS
        );
    }

    /**
     * Returns the lease duration assigned during a claim.
     *
     * @return lease duration in seconds
     */
    public int getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    /**
     * Sets the lease duration assigned during a claim.
     *
     * @param leaseDurationSeconds supported lease duration in seconds
     * @throws IllegalArgumentException when the duration is outside the supported range
     */
    public void setLeaseDurationSeconds(int leaseDurationSeconds) {
        this.leaseDurationSeconds = requireRange(
                "lease-duration-seconds",
                leaseDurationSeconds,
                MIN_LEASE_DURATION_SECONDS,
                MAX_LEASE_DURATION_SECONDS
        );
    }

    /**
     * Returns the maximum number of committed claims before terminal failure.
     *
     * @return attempt ceiling between one and {@link #MAX_MAX_ATTEMPTS}
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Sets the maximum number of committed claims before terminal failure.
     *
     * @param maxAttempts supported attempt ceiling
     * @throws IllegalArgumentException when the value is outside the supported range
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = requireRange("max-attempts", maxAttempts, 1, MAX_MAX_ATTEMPTS);
    }

    private static int requireRange(String property, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    "xtrmetl.etl.jobs." + property + " must be between "
                            + minimum + " and " + maximum + "; was " + value
            );
        }
        return value;
    }
}
