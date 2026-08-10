package com.xtrmetl.cdc.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void publicEnvironmentApiHasBeginnerReadableJavadoc() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/xtrmetl/cdc/util/EnvUtils.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Reads an optional environment variable"));
        assertTrue(source.contains("returns the supplied default when the variable is missing or blank"));
        assertTrue(source.contains("@param key environment variable name"));
        assertTrue(source.contains("@param defaultValue value returned when the variable is missing or blank"));
        assertTrue(source.contains("Reads a required environment variable"));
        assertTrue(source.contains("@throws IllegalStateException when the variable is missing or blank"));
    }
}
