package com.xtrmetl.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Exercises the registered WebFlux security chain rather than only inspecting source text.
 */
class GatewaySecurityConfigurationRuntimeTest {

    /** Default deny mode keeps health public while refusing application traffic. */
    @Test
    void denyModeExposesOnlyHealthAndInfoRoutes() {
        try (AnnotationConfigApplicationContext context = context("deny", false)) {
            WebTestClient client = client(context);

            client.get().uri("/actuator/health").exchange().expectStatus().isOk();
            client.get().uri("/actuator/info").exchange().expectStatus().isOk();
            client.get().uri("/etl/test").exchange().expectStatus().isForbidden();
            client.get().uri("/cdc/test").exchange().expectStatus().isForbidden();
            client.get().uri("/other").exchange().expectStatus().isForbidden();
        }
    }

    /** JWT mode requires authentication for routed workloads and accepts an authenticated JWT. */
    @Test
    void jwtModeProtectsWorkloadRoutesAndAcceptsAuthenticatedJwt() {
        try (AnnotationConfigApplicationContext context = context("jwt", true)) {
            WebTestClient client = client(context);

            client.get().uri("/etl/test").exchange().expectStatus().isUnauthorized();
            client.get().uri("/cdc/test").exchange().expectStatus().isUnauthorized();
            client.get().uri("/etl/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token")
                    .exchange()
                    .expectStatus().isUnauthorized();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("operator-123")))
                    .get().uri("/etl/test")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("etl reached");
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("operator-123")))
                    .get().uri("/cdc/test")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("cdc reached");
            client.mutateWith(mockJwt())
                    .get().uri("/other")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    /** Unknown modes are configuration errors rather than permissive fallbacks. */
    @Test
    void unknownModeFailsContextCreation() {
        assertThrows(RuntimeException.class, () -> {
            try (AnnotationConfigApplicationContext ignored = context("legacy-pass-through", false)) {
                // context creation itself must fail
            }
        });
    }

    private static AnnotationConfigApplicationContext context(String mode, boolean jwtDecoder) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("mightyetl.gateway.security.mode=" + mode).applyTo(context);
        context.register(GatewaySecurityConfiguration.class, TestRoutes.class);
        if (jwtDecoder) {
            context.register(RejectingDecoderConfiguration.class);
        }
        context.refresh();
        return context;
    }

    private static WebTestClient client(AnnotationConfigApplicationContext context) {
        return WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class TestRoutes {
        @Bean
        RouterFunction<ServerResponse> routes() {
            return RouterFunctions.route()
                    .GET("/actuator/health", ignored -> ServerResponse.ok().bodyValue("healthy"))
                    .GET("/actuator/info", ignored -> ServerResponse.ok().bodyValue("info"))
                    .GET("/etl/test", ignored -> ServerResponse.ok().bodyValue("etl reached"))
                    .GET("/cdc/test", ignored -> ServerResponse.ok().bodyValue("cdc reached"))
                    .GET("/other", ignored -> ServerResponse.ok().bodyValue("other reached"))
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RejectingDecoderConfiguration {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> Mono.error(new BadJwtException("test decoder rejects raw bearer input"));
        }
    }
}
