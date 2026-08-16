package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Starts the default Git-backed Config Server and proves blank repository
 * authority cannot become a running process.
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to a deployment-owned Git URI
 * before starting the default profile. Use {@code native} only for reviewed
 * local fixtures that do not need a remote.</p>
 */
class ConfigServerRepositoryAuthorityLiveTest {

    @Test
    void blankRepositoryUriFailsClosedBeforeGitAccess() {
        SpringApplication application = new SpringApplication(ConfigServerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(
                    "--server.port=0",
                    "--eureka.client.enabled=false",
                    "--eureka.client.register-with-eureka=false",
                    "--eureka.client.fetch-registry=false",
                    "--spring.cloud.config.server.git.uri=",
                    "--spring.cloud.config.server.git.clone-on-start=false"
            );
            fail("Blank CONFIG_REPO_URI must stop Config Server before Git access");
        } catch (Exception ex) {
            assertTrue(
                    containsAuthorityFailure(ex),
                    () -> "Startup must name the missing repository authority, but failed with: " + ex
            );
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    void unsetRepositoryUriFailsClosedBeforeGitAccess() {
        SpringApplication application = new SpringApplication(ConfigServerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(
                    "--server.port=0",
                    "--eureka.client.enabled=false",
                    "--eureka.client.register-with-eureka=false",
                    "--eureka.client.fetch-registry=false"
            );
            fail("Unset CONFIG_REPO_URI must stop Config Server before Git access");
        } catch (Exception ex) {
            assertTrue(
                    containsAuthorityFailure(ex) || containsUnresolvedPlaceholder(ex),
                    () -> "Startup must fail closed without a repository URI, but failed with: " + ex
            );
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private static boolean containsAuthorityFailure(Throwable thrown) {
        return messageChain(thrown).contains("CONFIG_REPO_URI must name a deployment-owned Git repository");
    }

    private static boolean containsUnresolvedPlaceholder(Throwable thrown) {
        String messages = messageChain(thrown);
        return messages.contains("Could not resolve placeholder 'CONFIG_REPO_URI'")
                || messages.contains("Could not resolve placeholder 'spring.cloud.config.server.git.uri'");
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
