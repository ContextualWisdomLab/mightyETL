package com.xtrmetl.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

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
 * the default profile. The {@code native} profile skips the Git URI check so
 * local fixtures can start. Combining {@code native} with {@code prod} or
 * {@code production} requires {@code xtrmetl.config.allow-native=true}.</p>
 */
@Order(ConfigDataEnvironmentPostProcessor.ORDER + 1)
public class ConfigServerRepositoryAuthorityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "config-server-repository-authority";
    static final String GIT_URI_PROPERTY = "spring.cloud.config.server.git.uri";
    static final String ALLOW_NATIVE_PROPERTY = "xtrmetl.config.allow-native";
    static final String NATIVE_PRODUCTION_MESSAGE =
            "native profile cannot be combined with prod or production; "
                    + "set CONFIG_REPO_URI and start the default Git profile, "
                    + "or set xtrmetl.config.allow-native=true only for an approved fixture";

    /**
     * Fails closed on incompatible profiles or missing Git authority.
     *
     * @param environment configurable application environment
     * @param application Spring application being prepared
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        requireCompatibleProfiles(environment);
        if (environment.acceptsProfiles(Profiles.of("native"))) {
            return;
        }
        String repositoryUri;
        try {
            repositoryUri = environment.getProperty(GIT_URI_PROPERTY);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE, ex);
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

    /**
     * Keeps {@code native} fixture-only unless an operator opts in.
     *
     * @param environment active Spring environment
     */
    static void requireCompatibleProfiles(Environment environment) {
        boolean nativeProfile = environment.acceptsProfiles(Profiles.of("native"));
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        boolean allowNative = Boolean.parseBoolean(
                environment.getProperty(ALLOW_NATIVE_PROPERTY, "false")
        );
        if (nativeProfile && production && !allowNative) {
            throw new IllegalStateException(NATIVE_PRODUCTION_MESSAGE);
        }
    }
}
