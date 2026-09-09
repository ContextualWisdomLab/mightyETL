package com.xtrmetl.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fails Config Server startup when the default Git backend has no repository authority.
 *
 * <p>The deployable property {@code spring.cloud.config.server.git.uri} is sourced only from
 * {@code CONFIG_REPO_URI} with no demo fallback. An unset or blank value means the operator never
 * approved a destination, so the default profile must refuse to start rather than serve later
 * requests from an unapproved repository. The {@code native} profile is excluded because it never
 * consults the Git backend.</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("!native")
public class ConfigServerRepositoryAuthorityValidator {

    private final String repositoryUri;

    /**
     * Binds the configured Git backend repository location.
     *
     * @param repositoryUri value of {@code spring.cloud.config.server.git.uri}, empty when unset
     */
    public ConfigServerRepositoryAuthorityValidator(
            @Value("${spring.cloud.config.server.git.uri:}") String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    /**
     * Rejects startup when no explicit repository authority was supplied.
     *
     * @throws IllegalStateException when the repository URI is missing or blank
     */
    @PostConstruct
    void requireExplicitRepositoryAuthority() {
        if (repositoryUri == null || repositoryUri.isBlank()) {
            throw new IllegalStateException(
                    "Config Server Git backend requires explicit repository authority: "
                    + "set CONFIG_REPO_URI to the deployment-approved repository and restart.");
        }
    }
}
