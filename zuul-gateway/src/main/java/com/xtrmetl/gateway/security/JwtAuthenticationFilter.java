package com.xtrmetl.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Legacy compatibility type retained temporarily so existing binary or source references fail safe
 * during the gateway authentication migration.
 *
 * <p>This class no longer parses bearer tokens, validates credentials, or creates an authenticated
 * principal. Authentication and authorization are owned exclusively by Spring Security's reactive
 * resource-server {@link org.springframework.security.web.server.SecurityWebFilterChain}. The
 * class is not registered as a Spring bean and should be removed once downstream source references
 * have migrated.</p>
 */
@Deprecated(forRemoval = true)
public class JwtAuthenticationFilter implements GlobalFilter {

    /**
     * Continues the gateway chain without modifying the Reactor security context.
     *
     * @param exchange current reactive HTTP exchange
     * @param chain remaining Spring Cloud Gateway filter chain
     * @return completion signal for the unchanged downstream filter chain
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange);
    }
}
