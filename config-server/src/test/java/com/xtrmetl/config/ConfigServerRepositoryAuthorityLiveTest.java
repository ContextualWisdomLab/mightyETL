package com.xtrmetl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Starts the default Git-backed Config Server and proves blank or demo
 * repository authority cannot become a running process, including when
 * {@code cloneOnStart} is true.
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to a deployment-owned Git URI
 * before starting the default profile. Use {@code native} only for reviewed
 * local fixtures that do not need a remote.</p>
 */
class ConfigServerRepositoryAuthorityLiveTest {

    @Test
    void blankRepositoryUriFailsClosedWithAuthorityMessage() {
        assertStartupFailure(
                ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE,
                "--spring.cloud.config.server.git.uri=",
                "--spring.cloud.config.server.git.clone-on-start=true"
        );
    }

    @Test
    void demoRepositoryUriFailsClosedBeforeCloneOnStart() {
        assertStartupFailure(
                ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE,
                "--spring.cloud.config.server.git.uri=https://github.com/your-repo/config-repo.git",
                "--spring.cloud.config.server.git.clone-on-start=true"
        );
    }

    @Test
    void unsetRepositoryUriFailsClosedBeforeGitAccess() {
        SpringApplication application = new SpringApplication(ConfigServerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(commonArgs());
            fail("Unset CONFIG_REPO_URI must stop Config Server before Git access");
        } catch (Exception ex) {
            assertTrue(
                    containsAuthorityFailure(ex) || containsUnresolvedPlaceholder(ex),
                    () -> "Startup must fail closed without a repository URI, but failed with: " + ex
            );
        } finally {
            closeQuietly(context);
        }
    }

    @Test
    void nativeCombinedWithProductionFailsClosed() {
        assertStartupFailure(
                ConfigServerRepositoryAuthorityEnvironmentPostProcessor.NATIVE_PRODUCTION_MESSAGE,
                "--spring.profiles.active=native,prod",
                "--spring.cloud.config.server.native.search-locations=classpath:/",
                "--spring.cloud.config.server.git.uri=https://git.example.internal/config-repo.git"
        );
    }

    private static void assertStartupFailure(String expectedMessage, String... extraArgs) {
        SpringApplication application = new SpringApplication(ConfigServerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(concat(commonArgs(), extraArgs));
            fail("Config Server must stop before Git access: " + expectedMessage);
        } catch (Exception ex) {
            assertTrue(
                    messageChain(ex).contains(expectedMessage),
                    () -> "Startup must name the missing repository authority, but failed with: " + ex
            );
        } finally {
            closeQuietly(context);
        }
    }

    private static String[] commonArgs() {
        return new String[] {
                "--server.port=0",
                "--eureka.client.enabled=false",
                "--eureka.client.register-with-eureka=false",
                "--eureka.client.fetch-registry=false"
        };
    }

    private static String[] concat(String[] first, String[] second) {
        String[] merged = new String[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    private static boolean containsAuthorityFailure(Throwable thrown) {
        return messageChain(thrown).contains(ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE);
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

    private static void closeQuietly(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}
