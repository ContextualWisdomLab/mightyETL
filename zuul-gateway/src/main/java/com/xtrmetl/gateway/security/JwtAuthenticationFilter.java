package com.xtrmetl.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A very small, gateway-level JWT-like authentication filter.
 *
 * <p>This module uses Spring Cloud Gateway (WebFlux), so this filter is implemented using the reactive APIs and
 * propagates authentication through the Reactor context.
 */
public class JwtAuthenticationFilter implements GlobalFilter {

    /**
     * Extracts a token from the {@code Authorization} header and, when valid, attaches an {@link Authentication}
     * to the Reactor context before continuing the filter chain.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange);
        if (token == null || !validateToken(token)) {
            return chain.filter(exchange);
        }

        Authentication authentication = createAuthentication(token);
        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private String extractToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        // Implement proper token validation logic
        // For this example, we'll consider "valid_token" as the only valid token
        return "valid_token".equals(token);
    }

    private Authentication createAuthentication(String token) {
        return new UsernamePasswordAuthenticationToken("user", token, AuthorityUtils.NO_AUTHORITIES);
    }
}
