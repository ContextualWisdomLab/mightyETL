package com.xtrmetl.cdc.util;

import org.springframework.lang.Nullable;

/**
 * Validates externally supplied CDC configuration values before they are used by database clients.
 *
 * <p>Rejected values are intentionally omitted from exception messages because configuration inputs
 * can contain credentials, control characters, or other sensitive operator data. Diagnostics identify
 * the configuration key and validation category without republishing the rejected value.</p>
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Validates a TCP port supplied as configuration text.
     *
     * @param value configured port text
     * @param key configuration key used for safe diagnostics
     * @return the trimmed decimal port text
     * @throws IllegalStateException when the value is missing, non-numeric, or outside 1 through 65535
     */
    public static String requireValidPort(@Nullable String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required port for " + key);
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[0-9]+$")) {
            throw new IllegalStateException("Invalid port for " + key);
        }
        try {
            int port = Integer.parseInt(trimmed);
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("Invalid port for " + key);
            }
            return trimmed;
        } catch (NumberFormatException ignored) {
            throw new IllegalStateException("Invalid port for " + key);
        }
    }

    /**
     * Validates a hostname or IP-literal-shaped host token accepted by the CDC configuration contract.
     *
     * @param value configured host text
     * @param key configuration key used for safe diagnostics
     * @return the trimmed host text
     * @throws IllegalStateException when the value is missing or contains unsupported characters
     */
    public static String requireValidHost(@Nullable String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required host for " + key);
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalStateException("Invalid host for " + key);
        }
        return trimmed;
    }

    /**
     * Validates a simple identifier used for CDC database and configuration names.
     *
     * @param value configured identifier text
     * @param key configuration key used for safe diagnostics
     * @return the trimmed identifier
     * @throws IllegalStateException when the value is missing or contains unsupported characters
     */
    public static String requireValidIdentifier(@Nullable String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required value for " + key);
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalStateException("Invalid value for " + key);
        }
        return trimmed;
    }
}
