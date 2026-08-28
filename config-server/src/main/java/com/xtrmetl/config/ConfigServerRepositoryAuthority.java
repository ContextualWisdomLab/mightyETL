package com.xtrmetl.config;

import java.net.URI;
import java.util.Locale;

/**
 * Decides whether a Config Server Git URI is an explicit deployment-owned authority.
 *
 * <p>Spring Cloud Config 5.0.4 treats a non-null empty URI as valid when
 * {@code cloneOnStart} is false, so a missing or blank {@code CONFIG_REPO_URI}
 * would otherwise start the process and defer Git work to the first request.
 * Call this check during environment preparation, before Config Server creates
 * its Git repository beans.</p>
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to the reviewed Git URI, then start
 * the default profile. Use the {@code native} profile only as the sole active
 * profile for local fixtures that must not depend on a remote.</p>
 */
public final class ConfigServerRepositoryAuthority {

    static final String MISSING_AUTHORITY_MESSAGE =
            "CONFIG_REPO_URI must name a deployment-owned Git repository; "
                    + "blank, unresolved, or demo authority is rejected before remote Git access";
    static final String MIXED_NATIVE_PROFILE_MESSAGE =
            "native profile must be the only active profile when Config Server uses local fixtures";

    private static final String UNRESOLVED_PLACEHOLDER = "${CONFIG_REPO_URI";
    private static final String RETIRED_DEMO_HOST = "github.com";
    private static final String RETIRED_DEMO_PATH = "/your-repo/config-repo";

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
        if (repositoryUri == null || repositoryUri.isBlank()) {
            throw new IllegalStateException(MISSING_AUTHORITY_MESSAGE);
        }
        String trimmed = repositoryUri.trim();
        if (trimmed.contains(UNRESOLVED_PLACEHOLDER) || isRetiredDemoRemote(trimmed)) {
            throw new IllegalStateException(MISSING_AUTHORITY_MESSAGE);
        }
    }

    /**
     * Ensures the local-fixture {@code native} profile cannot be composed with
     * another active profile to bypass Git repository authority validation.
     *
     * @param activeProfiles explicitly active Spring profiles
     * @return {@code true} when standalone native mode is active
     * @throws IllegalStateException when native is combined with another profile
     */
    public static boolean requireSafeProfileComposition(String... activeProfiles) {
        boolean nativeActive = false;
        int activeCount = 0;
        for (String profile : activeProfiles) {
            if (profile == null || profile.isBlank()) {
                continue;
            }
            activeCount++;
            if ("native".equalsIgnoreCase(profile.trim())) {
                nativeActive = true;
            }
        }
        if (nativeActive && activeCount != 1) {
            throw new IllegalStateException(MIXED_NATIVE_PROFILE_MESSAGE);
        }
        return nativeActive;
    }

    private static boolean isRetiredDemoRemote(String repositoryUri) {
        try {
            URI uri = URI.create(repositoryUri);
            if (!RETIRED_DEMO_HOST.equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            String path = uri.getPath();
            if (path == null) {
                return false;
            }
            String normalizedPath = path.toLowerCase(Locale.ROOT);
            while (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            return RETIRED_DEMO_PATH.equals(normalizedPath)
                    || (RETIRED_DEMO_PATH + ".git").equals(normalizedPath);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
