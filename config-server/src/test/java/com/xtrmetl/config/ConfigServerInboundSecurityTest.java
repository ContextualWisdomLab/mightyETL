package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the fail-closed inbound HTTP boundary for the reference-only Config Server profile.
 */
@SpringBootTest(
        classes = ConfigServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "spring.cloud.config.server.native.search-locations=classpath:/",
                "eureka.client.enabled=false"
        }
)
class ConfigServerInboundSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anonymousConfigurationReadIsDeniedWhileHealthRemainsPublic() {
        ResponseEntity<String> configuration = restTemplate.getForEntity("/application/default", String.class);
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        ResponseEntity<String> liveness = restTemplate.getForEntity(
                "/actuator/health/liveness",
                String.class
        );
        ResponseEntity<String> info = restTemplate.getForEntity("/actuator/info", String.class);
        ResponseEntity<String> nestedConfiguration = restTemplate.getForEntity(
                "/application/default/extra",
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, configuration.getStatusCode());
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertEquals(HttpStatus.OK, liveness.getStatusCode());
        assertEquals(HttpStatus.OK, info.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, nestedConfiguration.getStatusCode());
    }
}
