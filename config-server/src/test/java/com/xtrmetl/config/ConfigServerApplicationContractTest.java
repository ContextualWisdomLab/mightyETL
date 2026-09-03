package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the standalone Config Server bootstrap contract and its beginner-readable public documentation.
 */
class ConfigServerApplicationContractTest {

    @Test
    void bootstrapClassKeepsTheRequiredServerAnnotations() {
        assertNotNull(ConfigServerApplication.class.getAnnotation(SpringBootApplication.class));
        assertNotNull(ConfigServerApplication.class.getAnnotation(EnableConfigServer.class));
    }

    @Test
    void mainEntryPointRemainsPublicStatic() throws NoSuchMethodException {
        Method main = ConfigServerApplication.class.getDeclaredMethod("main", String[].class);

        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    void publicBootstrapApiHasBeginnerReadableJavadoc() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/xtrmetl/config/ConfigServerApplication.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Starts the standalone mightyETL Config Server"));
        assertTrue(source.contains("Launches the Config Server through Spring Boot"));
        assertTrue(source.contains("@param args command-line arguments passed to Spring Boot"));
    }
}