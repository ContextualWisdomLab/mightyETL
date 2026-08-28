package com.xtrmetl.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Config Server backend authority against demo or implicit network destinations.
 */
class ConfigServerRepositoryConfigurationTest {

    @Test
    void configRepositoryMustBeExplicitAndHaveNoDemoFallback() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        long exactAuthorityTokens = applicationYaml.lines()
                .map(String::trim)
                .filter("uri: ${CONFIG_REPO_URI}"::equals)
                .count();
        assertEquals(
                1L,
                exactAuthorityTokens,
                "Config Server must use exactly one CONFIG_REPO_URI token without a fallback"
        );
        assertFalse(
                applicationYaml.contains("${CONFIG_REPO_URI:"),
                "CONFIG_REPO_URI must not acquire an implicit default value"
        );
        assertFalse(
                applicationYaml.contains("your-repo/config-repo.git"),
                "A demo Git repository must never be a supported runtime fallback"
        );
    }

    @Test
    void environmentPostProcessorIsRegisteredBeforeConfigServerBeans() throws IOException {
        Path factoriesPath = Path.of("src/main/resources/META-INF/spring.factories");
        assertTrue(Files.exists(factoriesPath), "Repository authority must run as an EnvironmentPostProcessor");
        String factories = Files.readString(factoriesPath);
        assertTrue(
                factories.contains("org.springframework.boot.env.EnvironmentPostProcessor=\\\n"
                        + "com.xtrmetl.config.ConfigServerRepositoryAuthorityEnvironmentPostProcessor"),
                "EnvironmentPostProcessor registration must bind the repository-authority guard"
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
        assertTrue(doctoring.contains("ConfigServerRepositoryAuthorityEnvironmentPostProcessor"));
        assertTrue(doctoring.contains("unset or blank `CONFIG_REPO_URI`"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/security.html"));
        assertTrue(doctoring.contains("APA 7"));
        assertTrue(doctoring.contains("n.d."));
        assertTrue(doctoring.contains("Retrieved August 28, 2026"));
    }
}
