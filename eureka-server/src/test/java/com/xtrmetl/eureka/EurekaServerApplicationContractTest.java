package com.xtrmetl.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the standalone Eureka bootstrap contract and its beginner-readable public documentation.
 */
class EurekaServerApplicationContractTest {

    @Test
    void bootstrapClassKeepsTheRequiredServerAnnotations() {
        assertNotNull(EurekaServerApplication.class.getAnnotation(SpringBootApplication.class));
        assertNotNull(EurekaServerApplication.class.getAnnotation(EnableEurekaServer.class));
    }

    @Test
    void mainEntryPointRemainsPublicStatic() throws NoSuchMethodException {
        Method main = EurekaServerApplication.class.getDeclaredMethod("main", String[].class);

        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    void publicBootstrapApiHasBeginnerReadableJavadoc() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/xtrmetl/eureka/EurekaServerApplication.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Starts the standalone mightyETL Eureka service registry"));
        assertTrue(source.contains("Launches the Eureka registry through Spring Boot"));
        assertTrue(source.contains("@param args command-line arguments passed to Spring Boot"));
    }
}