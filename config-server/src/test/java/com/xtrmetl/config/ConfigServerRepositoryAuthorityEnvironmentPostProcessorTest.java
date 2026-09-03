package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the pre-JGit EnvironmentPostProcessor on realistic profile and URI combinations.
 */
class ConfigServerRepositoryAuthorityEnvironmentPostProcessorTest {

    private final ConfigServerRepositoryAuthorityEnvironmentPostProcessor processor =
            new ConfigServerRepositoryAuthorityEnvironmentPostProcessor();
    private final SpringApplication application = new SpringApplication(ConfigServerApplication.class);

    @Test
    void defaultProfileRejectsBlankAndDemoBeforeContextRefresh() {
        MockEnvironment blank = new MockEnvironment();
        blank.setProperty("spring.cloud.config.server.git.uri", "");
        assertThrows(
                IllegalStateException.class,
                () -> processor.postProcessEnvironment(blank, application)
        );

        MockEnvironment demo = new MockEnvironment();
        demo.setProperty(
                "spring.cloud.config.server.git.uri",
                "https://github.com/your-repo/config-repo.git"
        );
        assertEquals(
                ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE,
                assertThrows(
                        IllegalStateException.class,
                        () -> processor.postProcessEnvironment(demo, application)
                ).getMessage()
        );
    }

    @Test
    void defaultProfileAcceptsExplicitAuthorityAndTrimsPadding() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                "spring.cloud.config.server.git.uri",
                "  https://git.example.internal/config-repo.git  "
        );
        assertDoesNotThrow(() -> processor.postProcessEnvironment(environment, application));
        assertEquals(
                "https://git.example.internal/config-repo.git",
                environment.getProperty("spring.cloud.config.server.git.uri")
        );
    }

    @Test
    void nativeProfileSkipsGitUriCheck() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("native");
        assertDoesNotThrow(() -> processor.postProcessEnvironment(environment, application));
    }

    @Test
    void nativeCombinedWithProductionFailsUnlessExplicitlyAllowed() {
        MockEnvironment blocked = new MockEnvironment();
        blocked.setActiveProfiles("native", "prod");
        blocked.setProperty(
                "spring.cloud.config.server.git.uri",
                "https://git.example.internal/config-repo.git"
        );
        assertEquals(
                ConfigServerRepositoryAuthorityEnvironmentPostProcessor.NATIVE_PRODUCTION_MESSAGE,
                assertThrows(
                        IllegalStateException.class,
                        () -> processor.postProcessEnvironment(blocked, application)
                ).getMessage()
        );

        MockEnvironment allowed = new MockEnvironment();
        allowed.setActiveProfiles("native", "production");
        allowed.setProperty("xtrmetl.config.allow-native", "true");
        assertDoesNotThrow(() -> processor.postProcessEnvironment(allowed, application));
    }
}
