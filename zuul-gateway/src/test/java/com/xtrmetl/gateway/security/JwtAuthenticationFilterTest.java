package com.xtrmetl.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter();

    @Test
    void addsAuthenticationToReactorContextWhenValidToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("Authorization", "Bearer valid_token")
                        .build()
        );

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        GatewayFilterChain chain = ignored -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .doOnNext(authenticationRef::set)
                .then();

        jwtAuthenticationFilter.filter(exchange, chain).block();

        Authentication authentication = authenticationRef.get();
        assertNotNull(authentication);
        assertEquals("user", authentication.getPrincipal());
    }

    @Test
    void doesNotAttachAuthenticationWhenTokenIsInvalid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("Authorization", "Bearer invalid_token")
                        .build()
        );

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        GatewayFilterChain chain = ignored -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .doOnNext(authenticationRef::set)
                .then();

        jwtAuthenticationFilter.filter(exchange, chain).block();

        assertNull(authenticationRef.get());
    }

    @Test
    void doesNotAttachAuthenticationWhenTokenIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        GatewayFilterChain chain = ignored -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .doOnNext(authenticationRef::set)
                .then();

        jwtAuthenticationFilter.filter(exchange, chain).block();

        assertNull(authenticationRef.get());
    }
}
