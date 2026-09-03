package com.xtrmetl.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

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
                Pattern.compile("(?m)^\\s*uri:\\s*\\$\\{CONFIG_REPO_URI\\}\\s*$")
                        .matcher(applicationYaml)
                        .find(),
                "Config Server must require an exact operator-supplied CONFIG_REPO_URI token"
        );
        assertFalse(
                applicationYaml.contains("${CONFIG_REPO_URI:"),
                "Missing repository authority must not carry a YAML default colon"
        );
        assertFalse(
                applicationYaml.contains("your-repo/config-repo.git"),
                "A demo Git repository must never be a supported runtime fallback"
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
        assertTrue(doctoring.contains("ConfigServerRepositoryAuthorityValidator"));
        assertTrue(doctoring.contains("ConfigServerRepositoryAuthorityEnvironmentPostProcessor"));
        assertTrue(doctoring.contains("xtrmetl.config.allow-native"));
        assertTrue(doctoring.contains("blank"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/security.html"));
        assertTrue(doctoring.contains("APA 7"));
    }
}
