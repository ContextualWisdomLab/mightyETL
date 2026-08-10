package com.xtrmetl.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Config Server backend authority against demo or implicit network destinations.
 */
class ConfigServerRepositoryConfigurationTest {

    @Test
    void configRepositoryMustBeExplicitAndHaveNoDemoFallback() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(
                applicationYaml.contains("uri: ${CONFIG_REPO_URI}"),
                "Config Server must require an operator-supplied CONFIG_REPO_URI"
        );
        assertFalse(
                applicationYaml.contains("your-repo/config-repo.git"),
                "A demo Git repository must never be a supported runtime fallback"
        );
        assertFalse(
                applicationYaml.contains("CONFIG_REPO_URI:https://"),
                "Missing repository authority must fail closed before remote Git access"
        );
    }

    @Test
    void repositoryAuthorityHasSourceBackedDoctoring() throws IOException {
        Path doctoringPath = Path.of(
                "..",
                "docs",
                "doctoring",
                "config-server-repository-authority.md"
        );
        assertTrue(Files.exists(doctoringPath), "Repository authority requires canonical source-backed doctoring");

        String doctoring = Files.readString(doctoringPath);
        assertTrue(doctoring.contains("Spring Cloud Config 5.0.4"));
        assertTrue(doctoring.contains("spring.cloud.config.server.git.uri"));
        assertTrue(doctoring.contains("CONFIG_REPO_URI"));
        assertTrue(doctoring.contains("cloneOnStart"));
        assertTrue(doctoring.contains("skipSslValidation"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/security.html"));
        assertTrue(doctoring.contains("APA 7"));
    }
}
