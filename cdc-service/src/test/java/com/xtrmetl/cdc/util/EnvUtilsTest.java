package com.xtrmetl.cdc.util;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

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
    void publicEnvironmentContractIsDocumented() throws Exception {
        Path source = projectRoot().resolve(
                "cdc-service/src/main/java/com/xtrmetl/cdc/util/EnvUtils.java"
        );
        String sourceText = Files.readString(source);

        assertTrue(Pattern.compile("(?s)/\\*\\*.*?deployment configuration.*?\\*/\\s*public final class EnvUtils")
                .matcher(sourceText)
                .find());
        assertDocumentedPublicMethod(sourceText, "getEnv", "String key, String defaultValue");
        assertDocumentedPublicMethod(sourceText, "requireEnv", "String key");
    }

    private static void assertDocumentedPublicMethod(String source, String name, String parameters) {
        String expression = "(?s)/\\*\\*.*?\\*/\\s*public static String "
                + name + "\\(" + Pattern.quote(parameters) + "\\)";
        assertTrue(Pattern.compile(expression).matcher(source).find(),
                () -> "Missing public Javadoc for EnvUtils." + name);
    }

    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not find project root");
    }
}
