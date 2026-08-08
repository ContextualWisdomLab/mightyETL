package com.xtrmetl.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks down the historical filter so it can no longer manufacture authentication from example
 * bearer values while the registered Spring Security resource-server chain owns authentication.
 */
class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter();

    /** The historical example token must never create a trusted principal. */
    @Test
    void doesNotAttachAuthenticationForHistoricalExampleToken() {
        assertNoAuthentication("Bearer valid_token");
    }

    /** An arbitrary invalid token must not create a trusted principal. */
    @Test
    void doesNotAttachAuthenticationWhenTokenIsInvalid() {
        assertNoAuthentication("Bearer invalid_token");
    }

    /** Missing credentials must not create a trusted principal. */
    @Test
    void doesNotAttachAuthenticationWhenTokenIsMissing() {
        assertNoAuthentication(null);
    }

    private void assertNoAuthentication(String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/");
        if (authorizationHeader != null) {
            request.header("Authorization", authorizationHeader);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        GatewayFilterChain chain = ignored -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .doOnNext(authenticationRef::set)
                .then();

        jwtAuthenticationFilter.filter(exchange, chain).block();

        assertNull(authenticationRef.get());
    }
}
