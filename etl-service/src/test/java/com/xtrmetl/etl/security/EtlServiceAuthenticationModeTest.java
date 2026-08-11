package com.xtrmetl.etl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the registered ETL {@link SecurityConfig} rather than a source-text approximation.
 *
 * <p>The protected service currently accepts HTTP Basic on {@code /api/**}. The acquisition-ready
 * target in issue #161 requires independently authenticated ETL access without retaining Basic as
 * the secure production mechanism. This fail-first contract gives the current Basic filter a real
 * valid test principal and proves that those credentials can still authenticate through the actual
 * Spring Security filter chain.</p>
 */
@SpringJUnitConfig(classes = {
        SecurityConfig.class,
        EtlServiceAuthenticationModeTest.TestApplication.class
})
@WebAppConfiguration
class EtlServiceAuthenticationModeTest {

    private static final String TEST_USERNAME = "contract-user";
    private static final String TEST_PASSWORD = "contract-secret";

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
    void protectedApiRejectsHistoricalBasicCredentials() throws Exception {
        mockMvc.perform(get("/api/security-contract"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/security-contract")
                        .with(httpBasic(TEST_USERNAME, TEST_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    static class TestApplication {

        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername(TEST_USERNAME)
                            .password("{noop}" + TEST_PASSWORD)
                            .roles("TEST")
                            .build()
            );
        }

        @Bean
        SecurityContractController securityContractController() {
            return new SecurityContractController();
        }
    }

    @RestController
    static class SecurityContractController {

        @GetMapping("/api/security-contract")
        String protectedEndpoint() {
            return "ok";
        }
    }
}
