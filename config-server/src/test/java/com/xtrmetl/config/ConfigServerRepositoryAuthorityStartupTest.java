package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that default-profile startup refuses to run without repository authority.
 *
 * <p>Unlike the retired YAML-text assertions, these tests boot a real application context:
 * unset and blank {@code CONFIG_REPO_URI} values must fail the context, an explicit URI must
 * start, and the {@code native} profile must stay startable without any Git authority.</p>
 */
class ConfigServerRepositoryAuthorityStartupTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(
                    ConfigServerApplication.class,
                    ConfigServerRepositoryAuthorityValidator.class)
            .withPropertyValues("eureka.client.enabled=false");

    @Test
    void blankRepositoryAuthorityRefusesDefaultProfileStartup() {
        runner.withPropertyValues("spring.cloud.config.server.git.uri=")
                .run(context -> {
                    assertNotNull(
                            context.getStartupFailure(),
                            "Blank repository authority must fail default-profile startup");
                    assertTrue(
                            failureChainMentions(context.getStartupFailure(), "CONFIG_REPO_URI"),
                            "Startup failure must name the missing CONFIG_REPO_URI authority");
                });
    }

    @Test
    void unsetRepositoryAuthorityRefusesDefaultProfileStartup() {
        runner.withPropertyValues("spring.cloud.config.server.git.uri=${CONFIG_REPO_URI:}")
                .run(context -> {
                    assertNotNull(
                            context.getStartupFailure(),
                            "Unset repository authority must fail default-profile startup");
                    assertTrue(
                            failureChainMentions(context.getStartupFailure(), "CONFIG_REPO_URI"),
                            "Startup failure must name the missing CONFIG_REPO_URI authority");
                });
    }

    @Test
    void explicitRepositoryAuthorityAllowsDefaultProfileStartup() {
        runner.withPropertyValues(
                        "spring.cloud.config.server.git.uri=file:/tmp/xtrmetl-config-authority-proof")
                .run(context -> assertNull(
                        context.getStartupFailure(),
                        "An explicit repository authority must not block startup"));
    }

    @Test
    void nativeProfileStartsWithoutGitAuthority() {
        runner.withPropertyValues(
                        "spring.profiles.active=native",
                        "spring.cloud.config.server.git.uri=",
                        "spring.cloud.config.server.native.search-locations=classpath:/")
                .run(context -> assertNull(
                        context.getStartupFailure(),
                        "The native profile must stay startable without Git authority"));
    }

    private static boolean failureChainMentions(Throwable failure, String marker) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
