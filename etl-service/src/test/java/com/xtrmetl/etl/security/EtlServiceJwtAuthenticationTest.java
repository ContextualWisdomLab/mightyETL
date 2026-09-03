package com.xtrmetl.etl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.security.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.security.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Exercises mightyETL JWT mode through the real Spring Security bearer-token filter chain.
 */
@SpringJUnitConfig(classes = {
        SecurityConfig.class,
        EtlServiceJwtAuthenticationTest.TestApplication.class
})
@TestPropertySource(properties = {
        "mightyetl.security.mode=jwt",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid",
        "spring.security.oauth2.resourceserver.jwt.audiences=mightyetl-etl"
})
@WebAppConfiguration
class EtlServiceJwtAuthenticationTest {

    private static final String ACCEPTED_TOKEN = "accepted-token";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void jwtModeRejectsMissingInvalidAndHistoricalBasicCredentials() throws Exception {
        mockMvc.perform(get("/api/security-contract"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));

        mockMvc.perform(get("/api/security-contract")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer rejected-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));

        mockMvc.perform(get("/api/security-contract")
                        .with(httpBasic("historical-user", "historical-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    @Test
    void jwtModeAuthenticatesTheValidatedSubject() throws Exception {
        mockMvc.perform(get("/api/security-contract")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCEPTED_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("contract-user"));
    }

    @Configuration
    @EnableWebMvc
    static class TestApplication {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!ACCEPTED_TOKEN.equals(token)) {
                    throw new BadJwtException("Rejected test token");
                }
                Instant issuedAt = Instant.parse("2026-08-15T00:00:00Z");
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("contract-user")
                        .audience(List.of("mightyetl-etl"))
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(3600))
                        .build();
            };
        }

        @Bean
        SecurityContractController securityContractController() {
            return new SecurityContractController();
        }
    }

    @RestController
    static class SecurityContractController {

        @GetMapping("/api/security-contract")
        String protectedEndpoint(Authentication authentication) {
            return authentication.getName();
        }
    }
}
