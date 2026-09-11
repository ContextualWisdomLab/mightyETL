package com.xtrmetl.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Proves that JWT mode authenticates only tokens that carry a stable non-blank subject.
 */
class GatewayStableSubjectRuntimeTest {

    /** A decoded JWT without a usable subject must fail before the protected route is reached. */
    @Test
    void jwtModeRequiresNonBlankStableSubject() {
        try (AnnotationConfigApplicationContext context = context()) {
            WebTestClient client = client(context);

            client.get().uri("/etl/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer subjectless")
                    .exchange()
                    .expectStatus().isUnauthorized();
            client.get().uri("/etl/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer blank-subject")
                    .exchange()
                    .expectStatus().isUnauthorized();
            client.get().uri("/etl/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer subjectful")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("etl reached");
        }
    }

    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("mightyetl.gateway.security.mode=jwt").applyTo(context);
        context.register(
                GatewaySecurityConfiguration.class,
                TestRoutes.class,
                SubjectDecoderConfiguration.class
        );
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
                    .GET("/etl/test", ignored -> ServerResponse.ok().bodyValue("etl reached"))
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SubjectDecoderConfiguration {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> {
                Jwt.Builder builder = Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("scope", "etl");
                if ("blank-subject".equals(token)) {
                    builder.subject("   ");
                } else if ("subjectful".equals(token)) {
                    builder.subject("operator-123");
                }
                return Mono.just(builder.build());
            };
        }
    }
}
