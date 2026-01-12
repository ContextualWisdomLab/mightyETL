package com.xtrmetl.cdc.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvUtilsTest {

    @Test
    void getEnvReturnsDefaultWhenMissingOrBlank() {
        Map<String, String> env = new HashMap<>();

        assertEquals("default", EnvUtils.getEnv(env, "MISSING", "default"));

        env.put("BLANK", "");
        assertEquals("default", EnvUtils.getEnv(env, "BLANK", "default"));

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map rawEnv = env;
        rawEnv.put("NULL", null);
        assertEquals("default", EnvUtils.getEnv(env, "NULL", "default"));
    }

    @Test
    void getEnvReturnsValueWhenPresent() {
        Map<String, String> env = Map.of("KEY", "value");
        assertEquals("value", EnvUtils.getEnv(env, "KEY", "default"));
    }

    @Test
    void requireEnvThrowsWhenMissingOrBlank() {
        Map<String, String> env = new HashMap<>();

        assertThrows(IllegalStateException.class, () -> EnvUtils.requireEnv(env, "MISSING"));

        env.put("BLANK", " ");
        assertThrows(IllegalStateException.class, () -> EnvUtils.requireEnv(env, "BLANK"));
    }

    @Test
    void requireEnvReturnsValueWhenPresent() {
        Map<String, String> env = Map.of("KEY", "value");
        assertEquals("value", EnvUtils.requireEnv(env, "KEY"));
    }

    @Test
    void publicMethodsWorkForMissingEnv() {
        String key = "XTRMETL_TEST_MISSING_" + UUID.randomUUID();
        assertEquals("default", EnvUtils.getEnv(key, "default"));
        assertThrows(IllegalStateException.class, () -> EnvUtils.requireEnv(key));
    }
}
