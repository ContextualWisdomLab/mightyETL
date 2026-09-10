package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Starts the Config Server and proves repository/profile authority fails closed
 * before a Git backend can become active.
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to a deployment-owned Git URI
 * before starting the default profile. Use {@code native} only as the sole
 * active profile for reviewed local fixtures that do not need a remote.</p>
 */
class ConfigServerRepositoryAuthorityLiveTest {

    @Test
    void blankRepositoryUriFailsClosedBeforeGitAccess() {
        Exception failure = runExpectingFailure(
                applicationWithoutInheritedConfigRepoUri(),
                "--server.port=0",
                "--eureka.client.enabled=false",
                "--eureka.client.register-with-eureka=false",
                "--eureka.client.fetch-registry=false",
                "--spring.cloud.config.server.git.uri=",
                "--spring.cloud.config.server.git.clone-on-start=true"
        );

        assertTrue(
                containsAuthorityFailure(failure),
                () -> "Blank authority must fail with the repository-authority message: " + failure
        );
    }

    @Test
    void unsetRepositoryUriFailsClosedWithoutInheritedEnvironmentAuthority() {
        Exception failure = runExpectingFailure(
                applicationWithoutInheritedConfigRepoUri(),
                "--server.port=0",
                "--eureka.client.enabled=false",
                "--eureka.client.register-with-eureka=false",
                "--eureka.client.fetch-registry=false"
        );

        assertTrue(
                containsAuthorityFailure(failure),
                () -> "Unset authority must fail with the repository-authority message: " + failure
        );
    }

    @Test
    void retiredDemoUriFailsBeforeCloneOnStartCanContactGit() {
        Exception failure = runExpectingFailure(
                applicationWithoutInheritedConfigRepoUri(),
                "--server.port=0",
                "--eureka.client.enabled=false",
                "--eureka.client.register-with-eureka=false",
                "--eureka.client.fetch-registry=false",
                "--spring.cloud.config.server.git.uri=https://github.com/your-repo/config-repo.git",
                "--spring.cloud.config.server.git.clone-on-start=true"
        );

        assertTrue(
                containsAuthorityFailure(failure),
                () -> "Demo URI must be rejected before JGit clone-on-start: " + failure
        );
    }

    @Test
    void nativeProfileCannotBeCombinedWithAnotherActiveProfile() {
        Exception failure = runExpectingFailure(
                applicationWithoutInheritedConfigRepoUri(),
                "--server.port=0",
                "--eureka.client.enabled=false",
                "--eureka.client.register-with-eureka=false",
                "--eureka.client.fetch-registry=false",
                "--spring.profiles.active=native,default"
        );

        assertTrue(
                messageChain(failure).contains("native profile must be the only active profile"),
                () -> "Mixed native profile startup must fail closed: " + failure
        );
    }

    private static SpringApplication applicationWithoutInheritedConfigRepoUri() {
        SpringApplication application = new SpringApplication(ConfigServerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        return application;
    }

    private static Exception runExpectingFailure(SpringApplication application, String... args) {
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(args);
            fail("Config Server startup was expected to fail closed");
            throw new AssertionError("unreachable");
        } catch (Exception ex) {
            return ex;
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private static boolean containsAuthorityFailure(Throwable thrown) {
        return messageChain(thrown).contains("CONFIG_REPO_URI must name a deployment-owned Git repository");
    }

    private static String messageChain(Throwable thrown) {
        assertNotNull(thrown);
        StringBuilder messages = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }
}
