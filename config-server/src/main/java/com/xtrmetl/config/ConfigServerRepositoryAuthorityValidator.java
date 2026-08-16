package com.xtrmetl.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Defense-in-depth fail-closed check after context refresh begins.
 *
 * <p>{@link ConfigServerRepositoryAuthorityEnvironmentPostProcessor} is the
 * authority that runs before JGit. This bean remains so a missing processor
 * registration still stops the default profile. The {@code native} profile is
 * excluded so reviewed local fixtures can start without a remote. Do not use
 * {@code native} as a production fallback.</p>
 */
@Component
@Profile("!native")
public class ConfigServerRepositoryAuthorityValidator implements InitializingBean {

    private final String repositoryUri;

    /**
     * @param repositoryUri bound Git backend URI; empty when unset
     */
    public ConfigServerRepositoryAuthorityValidator(
            @Value("${spring.cloud.config.server.git.uri:}") String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    /**
     * Rejects blank, unresolved, or demo repository authority before Git access.
     */
    @Override
    public void afterPropertiesSet() {
        ConfigServerRepositoryAuthority.requireExplicitRepository(repositoryUri);
    }
}
