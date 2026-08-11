package com.xtrmetl.cdc.util;

import org.springframework.lang.Nullable;

/**
 * Validates bounded replica database configuration without republishing rejected values.
 *
 * <p>Successful values are returned in their trimmed canonical form. Failure messages identify
 * the configuration key and validation class only, because rejected deployment values can contain
 * connection coordinates, control characters, or other secret-adjacent diagnostic material.</p>
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Validates a decimal TCP port in the inclusive range 1 through 65535.
     *
     * @param value configured port text, possibly surrounded by whitespace
     * @param key stable configuration key used in failure diagnostics
     * @return the trimmed decimal port
     * @throws IllegalStateException when the value is missing, non-decimal, or outside the port range
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
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid port for " + key);
        }
    }

    /**
     * Validates a hostname or numeric host token accepted by the replica JDBC configuration.
     *
     * @param value configured host text, possibly surrounded by whitespace
     * @param key stable configuration key used in failure diagnostics
     * @return the trimmed host token
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
     * Validates a simple configuration identifier such as a replica database name.
     *
     * @param value configured identifier text, possibly surrounded by whitespace
     * @param key stable configuration key used in failure diagnostics
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
