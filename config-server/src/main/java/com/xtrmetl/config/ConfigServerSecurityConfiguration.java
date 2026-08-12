package com.xtrmetl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Defines the credential-free fail-closed HTTP posture for the reference-only Config Server.
 *
 * <p>The repository does not invent a production service identity. Until a deployment-owned
 * authentication mechanism is selected and proven under issue #193, only health and info actuator
 * endpoints are intentionally public and every configuration-resource request is denied. A future
 * authenticated production profile must replace this reference-only posture through a separately
 * reviewed security contract rather than weakening this default.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ConfigServerSecurityConfiguration {

    /**
     * Builds the reference-only Config Server security chain.
     *
     * @param http Spring Security's servlet HTTP configuration builder
     * @return the configured filter chain
     * @throws Exception when Spring Security cannot build the filter chain
     */
    @Bean
    public SecurityFilterChain configServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
