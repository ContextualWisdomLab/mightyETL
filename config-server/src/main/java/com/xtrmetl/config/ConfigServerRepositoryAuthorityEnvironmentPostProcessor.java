package com.xtrmetl.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Rejects blank, demo, unresolved, or request-templated Git authority before
 * JGit {@code afterPropertiesSet}.
 *
 * <p>Spring Cloud Config 5.0.4 constructs {@code JGitEnvironmentRepository} as
 * an unordered {@code InitializingBean}. A validator bean can therefore run
 * after {@code cloneOnStart=true} has already contacted a remote. This
 * processor runs after config-data load and before context refresh.</p>
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to a reviewed Git URI, then start
 * the default profile. The {@code native} profile skips the Git URI check only
 * when it is the sole active profile.</p>
 */
@Order(ConfigDataEnvironmentPostProcessor.ORDER + 1)
public class ConfigServerRepositoryAuthorityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "config-server-repository-authority";
    static final String GIT_URI_PROPERTY = "spring.cloud.config.server.git.uri";

    /**
     * Fails closed on incompatible profiles or missing Git authority.
     *
     * @param environment configurable application environment
     * @param application Spring application being prepared
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (ConfigServerRepositoryAuthority.requireSafeProfileComposition(environment.getActiveProfiles())) {
            return;
        }
        String repositoryUri;
        try {
            repositoryUri = environment.getProperty(GIT_URI_PROPERTY);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            throw new IllegalStateException(
                    ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE,
                    unresolvedPlaceholder
            );
        }
        ConfigServerRepositoryAuthority.requireExplicitRepository(repositoryUri);
        String trimmed = ConfigServerRepositoryAuthority.trimAuthority(repositoryUri);
        if (!trimmed.equals(repositoryUri)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    PROPERTY_SOURCE_NAME,
                    Map.of(GIT_URI_PROPERTY, trimmed)
            ));
        }
    }
}
