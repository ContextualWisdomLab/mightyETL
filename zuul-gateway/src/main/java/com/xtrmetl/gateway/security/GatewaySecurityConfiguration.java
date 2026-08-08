package com.xtrmetl.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Locale;

/**
 * Defines the gateway's fail-closed authentication boundary.
 *
 * <p>The default {@code deny} mode keeps the gateway process independently startable without
 * inventing an identity-provider URL, public key, client secret, or other deployment credential.
 * It exposes only the narrow actuator health and information endpoints and denies all routed
 * application traffic. Operators enable standards-based bearer authentication explicitly with
 * {@code mightyetl.gateway.security.mode=jwt} and Spring Boot's supported reactive resource-server
 * JWT properties. In JWT mode, Spring Security validates bearer tokens before requests can reach
 * the ETL or CDC routes.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    static final String SECURITY_MODE_PROPERTY = "mightyetl.gateway.security.mode";
    static final String DENY_MODE = "deny";
    static final String JWT_MODE = "jwt";

    /**
     * Builds the single reactive security chain used by the gateway.
     *
     * <p>{@code deny} is the secure standalone default. {@code jwt} enables OAuth 2.0 Resource
     * Server JWT authentication and therefore also requires a valid {@code ReactiveJwtDecoder}
     * supplied through supported Spring Boot issuer, JWK-set, public-key configuration, or an
     * explicit application bean. Unknown values fail application startup instead of silently
     * weakening authentication.</p>
     *
     * @param http Spring Security's reactive HTTP security builder
     * @param configuredMode configured gateway security mode; defaults to {@code deny}
     * @return immutable reactive security filter chain
     * @throws IllegalArgumentException when the configured security mode is unsupported
     */
    @Bean
    public SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${" + SECURITY_MODE_PROPERTY + ":" + DENY_MODE + "}") String configuredMode
    ) {
        String mode = configuredMode.trim().toLowerCase(Locale.ROOT);
        disableBrowserSessionFeatures(http);

        if (DENY_MODE.equals(mode)) {
            return http
                    .authorizeExchange(exchanges -> exchanges
                            .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                            .anyExchange().denyAll())
                    .build();
        }
        if (JWT_MODE.equals(mode)) {
            return http
                    .authorizeExchange(exchanges -> exchanges
                            .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                            .pathMatchers("/etl/**", "/cdc/**").authenticated()
                            .anyExchange().denyAll())
                    .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                    .build();
        }
        throw new IllegalArgumentException(
                "Unsupported " + SECURITY_MODE_PROPERTY + " value: " + configuredMode
        );
    }

    private static void disableBrowserSessionFeatures(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        http.logout(ServerHttpSecurity.LogoutSpec::disable);
    }
}
