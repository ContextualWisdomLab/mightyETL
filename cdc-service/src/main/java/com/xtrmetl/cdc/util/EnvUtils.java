package com.xtrmetl.cdc.util;

import java.util.Map;

/**
 * Reads process environment variables for CDC deployment configuration.
 *
 * <p>Optional lookups use caller-provided defaults for missing or blank values. Required
 * lookups fail closed when a value is missing or blank. Returned configured values are not
 * transformed by this utility.</p>
 */
public final class EnvUtils {

    private EnvUtils() {}

    /**
     * Reads an optional environment variable and returns the supplied default when the variable is missing or blank.
     *
     * @param key environment variable name
     * @param defaultValue value returned when the variable is missing or blank
     * @return configured value when present and non-blank, otherwise {@code defaultValue}
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
     * Reads a required environment variable.
     *
     * @param key environment variable name
     * @return configured non-blank value
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
