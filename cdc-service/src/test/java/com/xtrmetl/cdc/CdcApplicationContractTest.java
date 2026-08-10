package com.xtrmetl.cdc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the standalone CDC bootstrap contract and its beginner-readable public documentation.
 */
class CdcApplicationContractTest {

    @Test
    void bootstrapClassKeepsTheRequiredCdcAnnotations() {
        assertNotNull(CdcApplication.class.getAnnotation(SpringBootApplication.class));
        assertNotNull(CdcApplication.class.getAnnotation(EnableDiscoveryClient.class));
        assertNotNull(CdcApplication.class.getAnnotation(EnableKafka.class));
    }

    @Test
    void mainEntryPointRemainsPublicStatic() throws NoSuchMethodException {
        Method main = CdcApplication.class.getDeclaredMethod("main", String[].class);

        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    void publicBootstrapApiHasBeginnerReadableJavadoc() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/xtrmetl/cdc/CdcApplication.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Starts the standalone mightyETL CDC service"));
        assertTrue(source.contains("service discovery and Kafka support during CDC bootstrap"));
        assertTrue(source.contains("Launches the CDC service through Spring Boot"));
        assertTrue(source.contains("@param args command-line arguments passed to Spring Boot"));
    }
}
