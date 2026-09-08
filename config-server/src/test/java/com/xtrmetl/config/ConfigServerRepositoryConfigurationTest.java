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
    void boot35DiscoversTheProcessorThroughSpringFactories() throws IOException {
        String factories = Files.readString(Path.of(
                "src/main/resources/META-INF/spring.factories"
        ));
        assertTrue(
                factories.contains(
                        "com.xtrmetl.config.ConfigServerRepositoryAuthorityEnvironmentPostProcessor"
                ),
                "Boot 3.5.16 EnvironmentPostProcessor discovery uses META-INF/spring.factories"
        );
    }

    @Test
    void processorDoesNotCarryUnsupportedAlternateRegistration() {
        assertFalse(
                Files.exists(Path.of(
                        "src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor"
                )),
                "EnvironmentPostProcessor registration is owned by META-INF/spring.factories"
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
        assertTrue(doctoring.contains("META-INF/spring.factories"));
        assertTrue(doctoring.contains("native profile must be the only active profile"));
        assertTrue(doctoring.contains("unset or blank CONFIG_REPO_URI"));
        assertTrue(doctoring.contains("blank"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/git-backend.html"));
        assertTrue(doctoring.contains("https://docs.spring.io/spring-cloud-config/reference/server/security.html"));
        assertTrue(doctoring.contains("APA 7"));
    }
}
