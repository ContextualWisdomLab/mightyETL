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
 * Defines the source and operator-contract boundary for replacing the historical example-token
 * gateway filter with Spring Security's reactive OAuth 2.0 Resource Server JWT support.
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
     * Requires an explicit fail-closed deployment default and an operator contract instead of an
     * invented issuer, key URL, or secret baked into source control.
     *
     * @throws IOException when an authoritative source file cannot be read
     */
    @Test
    void publishesFailClosedDeploymentAndStandardsContract() throws IOException {
        Path root = projectRoot();
        String application = Files.readString(
                root.resolve("zuul-gateway/src/main/resources/application.yml"),
                StandardCharsets.UTF_8
        );
        Path operatorContract = root.resolve("docs/security/gateway-jwt.md");

        assertTrue(application.contains("MIGHTYETL_GATEWAY_SECURITY_MODE:deny"));
        assertFalse(application.contains("idp.example"));
        assertTrue(Files.exists(operatorContract));
        String contract = Files.readString(operatorContract, StandardCharsets.UTF_8);
        assertTrue(contract.contains("RFC 7519"));
        assertTrue(contract.contains("RFC 8725"));
        assertTrue(contract.contains("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        assertTrue(contract.contains("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"));
        assertTrue(contract.contains("spring.security.oauth2.resourceserver.jwt.audiences"));
        assertTrue(contract.contains("MIGHTYETL_GATEWAY_SECURITY_MODE=jwt"));
        assertTrue(contract.contains("Authorization"));
        assertTrue(contract.contains("non-blank `sub`"));
        assertTrue(contract.contains("fails authentication before routing"));
        assertTrue(contract.contains("rollback"));
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
