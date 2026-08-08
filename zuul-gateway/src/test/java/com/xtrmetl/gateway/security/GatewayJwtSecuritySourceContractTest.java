package com.xtrmetl.gateway.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines the minimum source-level boundary for replacing the historical example-token gateway
 * filter with Spring Security's reactive OAuth 2.0 Resource Server JWT support.
 *
 * <p>This contract intentionally fails on the legacy implementation before any production change:
 * the gateway still depends on JJWT directly, accepts the literal {@code valid_token}, and has no
 * explicit reactive resource-server security configuration.</p>
 */
class GatewayJwtSecuritySourceContractTest {

    /**
     * Requires the gateway to use Spring Security's supported resource-server stack rather than the
     * historical hand-written example-token validator.
     *
     * @throws IOException when an authoritative source file cannot be read
     */
    @Test
    void replacesPlaceholderTokenValidationWithReactiveResourceServerJwt() throws IOException {
        Path root = projectRoot();
        String gatewayPom = Files.readString(root.resolve("zuul-gateway/pom.xml"), StandardCharsets.UTF_8);
        String legacyFilter = Files.readString(
                root.resolve("zuul-gateway/src/main/java/com/xtrmetl/gateway/security/JwtAuthenticationFilter.java"),
                StandardCharsets.UTF_8
        );
        Path securityConfiguration = root.resolve(
                "zuul-gateway/src/main/java/com/xtrmetl/gateway/security/GatewaySecurityConfiguration.java"
        );

        assertTrue(gatewayPom.contains("spring-boot-starter-oauth2-resource-server"));
        assertFalse(gatewayPom.contains("<artifactId>jjwt</artifactId>"));
        assertFalse(legacyFilter.contains("\"valid_token\""));
        assertTrue(Files.exists(securityConfiguration));
    }

    /**
     * Locates the multi-module repository root from Maven module or reactor execution.
     *
     * @return absolute repository root
     */
    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPomParent = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPomParent = current;
            }
            current = current.getParent();
        }
        if (lastPomParent != null) {
            return lastPomParent;
        }
        throw new IllegalStateException("Could not find project root");
    }
}
