package com.xtrmetl.config;

/**
 * Decides whether a Config Server Git URI is an explicit deployment-owned authority.
 *
 * <p>Spring Cloud Config 5.0.4 treats a non-null empty URI as valid when
 * {@code cloneOnStart} is false, so a missing or blank {@code CONFIG_REPO_URI}
 * would otherwise start the process and defer Git work to the first request.
 * Call this check during default-profile startup, before any clone.</p>
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to the reviewed Git URI, then start
 * the default profile. Use the {@code native} profile only for local fixtures
 * that must not depend on a remote.</p>
 */
public final class ConfigServerRepositoryAuthority {

    static final String MISSING_AUTHORITY_MESSAGE =
            "CONFIG_REPO_URI must name a deployment-owned Git repository; "
                    + "blank, unresolved, or demo authority is rejected before remote Git access";

    private static final String UNRESOLVED_PLACEHOLDER = "${CONFIG_REPO_URI";
    private static final String DEMO_REMOTE = "your-repo/config-repo.git";

    private ConfigServerRepositoryAuthority() {
    }

    /**
     * Rejects repository values that are not an operator-supplied destination.
     *
     * @param repositoryUri bound {@code spring.cloud.config.server.git.uri} value
     * @throws IllegalStateException when the URI is missing, blank, still a
     *                               placeholder, or the retired demo remote
     */
    public static void requireExplicitRepository(String repositoryUri) {
        if (repositoryUri == null
                || repositoryUri.isBlank()
                || repositoryUri.contains(UNRESOLVED_PLACEHOLDER)
                || repositoryUri.contains(DEMO_REMOTE)) {
            throw new IllegalStateException(MISSING_AUTHORITY_MESSAGE);
        }
    }
}
