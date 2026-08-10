package com.xtrmetl.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the standalone gateway bootstrap contract and its beginner-readable public documentation.
 */
class ZuulGatewayApplicationContractTest {

    @Test
    void bootstrapClassKeepsTheRequiredGatewayAnnotations() {
        assertNotNull(ZuulGatewayApplication.class.getAnnotation(SpringBootApplication.class));
        assertNotNull(ZuulGatewayApplication.class.getAnnotation(EnableDiscoveryClient.class));
    }

    @Test
    void mainEntryPointRemainsPublicStatic() throws NoSuchMethodException {
        Method main = ZuulGatewayApplication.class.getDeclaredMethod("main", String[].class);

        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    void publicBootstrapApiHasBeginnerReadableJavadoc() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/xtrmetl/gateway/ZuulGatewayApplication.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("Starts the standalone mightyETL gateway"));
        assertTrue(source.contains("service discovery during gateway bootstrap"));
        assertTrue(source.contains("Launches the gateway through Spring Boot"));
        assertTrue(source.contains("@param args command-line arguments passed to Spring Boot"));
    }
}
