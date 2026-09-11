package com.xtrmetl.cdc.util;

import java.util.Map;

/**
 * Reads deployment configuration from environment variables.
 *
 * <p>Optional values use a caller-provided fallback when the variable is missing or blank.
 * Required values fail closed with {@link IllegalStateException} when the variable is missing or
 * blank.</p>
 */
public final class EnvUtils {

    private EnvUtils() {}

    /**
     * Reads an environment variable with a default fallback.
     *
     * @param key environment variable name
     * @param defaultValue value returned when the variable is missing or blank
     * @return the non-blank environment value, or {@code defaultValue}
     */
    public static String getEnv(String key, String defaultValue) {
        return getEnv(System.getenv(), key, defaultValue);
    }

    static String getEnv(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Reads a required environment variable and fails closed when it is unavailable.
     *
     * @param key environment variable name
     * @return the non-blank environment value
     * @throws IllegalStateException when the variable is missing or blank
     */
    public static String requireEnv(String key) {
        return requireEnv(System.getenv(), key);
    }

    static String requireEnv(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
