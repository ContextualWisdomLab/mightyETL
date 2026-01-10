package com.xtrmetl.cdc.util;

public final class ValidationUtils {

    private ValidationUtils() {}

    public static String requireValidPort(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required port for " + key);
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[0-9]+$")) {
            throw new IllegalStateException("Invalid port for " + key + ": " + value);
        }
        try {
            int port = Integer.parseInt(trimmed);
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("Invalid port for " + key + ": " + value);
            }
            return trimmed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid port for " + key + ": " + value, e);
        }
    }

    public static String requireValidHost(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required host for " + key);
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalStateException("Invalid host for " + key + ": " + value);
        }
        return trimmed;
    }

    public static String requireValidIdentifier(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required value for " + key);
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalStateException("Invalid value for " + key + ": " + value);
        }
        return trimmed;
    }
}
