package com.xtrmetl.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaRegistryInboundSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anonymousRegistryQueriesAreRejectedAtTheInboundHttpBoundary() {
        ResponseEntity<String> response = restTemplate.getForEntity("/eureka/apps", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED
                        || response.getStatusCode() == HttpStatus.FORBIDDEN,
                () -> "anonymous Eureka registry query must be rejected, but returned "
                        + response.getStatusCode()
        );
    }
}
