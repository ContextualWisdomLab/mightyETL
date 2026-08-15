package com.xtrmetl.etl.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies fail-closed parsing and deployment-authority requirements for ETL security modes.
 */
class EtlSecurityModeTest {

    @Test
    void absentAndExplicitDenyConfigurationSelectTheSecureDefault() {
        assertEquals(EtlSecurityMode.DENY, EtlSecurityMode.parse(null));
        assertEquals(EtlSecurityMode.DENY, EtlSecurityMode.parse(""));
        assertEquals(EtlSecurityMode.DENY, EtlSecurityMode.parse("   "));
        assertEquals(EtlSecurityMode.DENY, EtlSecurityMode.parse("deny"));
        assertEquals(EtlSecurityMode.DENY, EtlSecurityMode.parse(" DENY "));
    }

    @Test
    void jwtConfigurationIsCaseInsensitiveButUnknownModesFailClosed() {
        assertEquals(EtlSecurityMode.JWT, EtlSecurityMode.parse("jwt"));
        assertEquals(EtlSecurityMode.JWT, EtlSecurityMode.parse(" JWT "));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> EtlSecurityMode.parse("basic")
        );
        assertTrue(failure.getMessage().contains("Unsupported mightyETL security mode"));
    }

    @Test
    void jwtModeRequiresDeploymentOwnedIssuerAuthority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.oauth2.resourceserver.jwt.audiences", "mightyetl-etl");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SecurityConfig.requireJwtTrustConfiguration(environment)
        );
        assertTrue(failure.getMessage().contains("issuer-uri"));
    }

    @Test
    void jwtModeRequiresDeploymentOwnedAudienceAuthority() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://issuer.example.invalid"
                );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SecurityConfig.requireJwtTrustConfiguration(environment)
        );
        assertTrue(failure.getMessage().contains("audiences"));
    }

    @Test
    void completeJwtTrustAuthorityPassesThePreflight() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://issuer.example.invalid"
                )
                .withProperty("spring.security.oauth2.resourceserver.jwt.audiences", "mightyetl-etl");

        assertDoesNotThrow(() -> SecurityConfig.requireJwtTrustConfiguration(environment));
    }
}
