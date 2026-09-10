package com.xtrmetl.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Validates Config Server repository authority after config data is loaded and
 * before the application context creates Spring Cloud Config Git components.
 *
 * <p>This boundary intentionally runs before bean initialization so
 * {@code clone-on-start=true} cannot contact an unreviewed or retired remote.
 * Standalone {@code native} mode remains available for reviewed local fixtures,
 * but native cannot be combined with another active profile.</p>
 */
public final class ConfigServerRepositoryAuthorityEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final String REPOSITORY_URI_PROPERTY =
            "spring.cloud.config.server.git.uri";

    /**
     * Runs immediately after Spring Boot has loaded normal ConfigData.
     *
     * @return the processor order
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    /**
     * Enforces profile composition and Git destination authority before context refresh.
     *
     * @param environment prepared Spring environment
     * @param application application being started
     */
    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        boolean nativeOnly = ConfigServerRepositoryAuthority.requireSafeProfileComposition(
                environment.getActiveProfiles()
        );
        if (nativeOnly) {
            return;
        }

        String repositoryUri;
        try {
            repositoryUri = environment.getProperty(REPOSITORY_URI_PROPERTY);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            throw new IllegalStateException(
                    ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE,
                    unresolvedPlaceholder
            );
        }
        ConfigServerRepositoryAuthority.requireExplicitRepository(repositoryUri);
    }
}
