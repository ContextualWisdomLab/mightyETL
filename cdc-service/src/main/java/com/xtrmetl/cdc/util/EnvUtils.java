package com.xtrmetl.cdc.util;

import java.util.Map;

public final class EnvUtils {

    private EnvUtils() {}

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
