package com.xtrmetl.etl.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.HttpStatusAccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configures the independently enforced HTTP authentication boundary for the ETL service.
 *
 * <p>The secure repository default is {@code deny}: health and information probes remain
 * available, while direct workload APIs are unavailable until the deployment explicitly selects
 * JWT mode and supplies issuer and audience authority. Historical HTTP Basic credentials are never
 * accepted by this configuration.</p>
 */
@Configuration
public class SecurityConfig {

    private static final String JWT_ISSUER_PROPERTY =
            "spring.security.oauth2.resourceserver.jwt.issuer-uri";
    private static final String JWT_AUDIENCES_PROPERTY =
            "spring.security.oauth2.resourceserver.jwt.audiences";
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /**
     * Builds the service security filter chain from deployment-owned authentication settings.
     *
     * <p>In {@code jwt} mode Spring Security validates bearer tokens through its maintained OAuth
     * 2.0 Resource Server support. The configured issuer supplies key and issuer authority, while
     * the configured audience prevents a token minted for another service from being accepted by
     * mightyETL. In the default {@code deny} mode no credential can reach {@code /api/**}.</p>
     *
     * @param http Spring Security HTTP configuration to build
     * @param configuredMode explicit mightyETL security mode, defaulting to {@code deny}
     * @param environment deployment properties used to verify JWT trust authority
     * @return configured stateless security filter chain
     * @throws Exception when Spring Security cannot build the filter chain
     * @throws IllegalArgumentException when the mode is unknown or JWT trust settings are missing
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${mightyetl.security.mode:${xtrmetl.security.mode:${ETL_SECURITY_MODE:deny}}}")
            String configuredMode,
            Environment environment
    ) throws Exception {
        EtlSecurityMode securityMode = EtlSecurityMode.parse(configuredMode);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityMode == EtlSecurityMode.JWT) {
            requireJwtTrustConfiguration(environment);
            http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll())
                    .oauth2ResourceServer(resourceServer ->
                            resourceServer.jwt(Customizer.withDefaults()));
        } else {
            http
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                            .accessDeniedHandler(new HttpStatusAccessDeniedHandler(HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                            .requestMatchers("/api/**").denyAll()
                            .anyRequest().permitAll());
        }

        return http.build();
    }

    /**
     * Requires the deployment authority needed for issuer and audience validation in JWT mode.
     *
     * @param environment deployment property source
     * @throws IllegalArgumentException when issuer or audience authority is missing
     */
    static void requireJwtTrustConfiguration(Environment environment) {
        requireNonBlank(environment, JWT_ISSUER_PROPERTY);
        requireNonBlank(environment, JWT_AUDIENCES_PROPERTY);
    }

    private static void requireNonBlank(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT security mode requires non-blank property: " + propertyName
            );
        }
    }
}
