package com.xtrmetl.config;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a Config Server Git URI is an explicit deployment-owned authority.
 *
 * <p>Spring Cloud Config 5.0.4 treats a non-null empty URI as valid when
 * {@code cloneOnStart} is false, so a missing or blank {@code CONFIG_REPO_URI}
 * would otherwise start the process and defer Git work to the first request.
 * Call this check from
 * {@link ConfigServerRepositoryAuthorityEnvironmentPostProcessor} during
 * default-profile startup, before JGit {@code afterPropertiesSet}.</p>
 *
 * <p>Operators: set {@code CONFIG_REPO_URI} to the reviewed Git URI, then start
 * the default profile. Use the {@code native} profile only for local fixtures
 * that must not depend on a remote. Do not combine {@code native} with
 * {@code prod} or {@code production} unless {@code xtrmetl.config.allow-native=true}
 * is an approved fixture exception.</p>
 */
public final class ConfigServerRepositoryAuthority {

    static final String MISSING_AUTHORITY_MESSAGE =
            "CONFIG_REPO_URI must name a deployment-owned Git repository; "
                    + "blank, unresolved, or demo authority is rejected before remote Git access";

    static final String REQUEST_TEMPLATE_MESSAGE =
            "CONFIG_REPO_URI must be a concrete repository destination; "
                    + "{application}, {profile}, and {label} placeholders are rejected";

    private static final String UNRESOLVED_PLACEHOLDER = "${CONFIG_REPO_URI";
    private static final String RETIRED_DEMO_HOST = "github.com";
    private static final String RETIRED_DEMO_PATH = "/your-repo/config-repo";
    private static final Pattern REQUEST_TEMPLATE =
            Pattern.compile("\\{(application|profile|label)\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCP_DEMO = Pattern.compile(
            "(?i)(?:^|@)github\\.com[:/]your-repo/config-repo(?:\\.git)?/?$"
    );

    private ConfigServerRepositoryAuthority() {
    }

    /**
     * Rejects repository values that are not an operator-supplied destination.
     *
     * @param repositoryUri bound {@code spring.cloud.config.server.git.uri} value
     * @throws IllegalStateException when the URI is missing, blank after Unicode
     *                               trim, still a placeholder, the retired demo
     *                               remote, or a request-templated Git location
     */
    public static void requireExplicitRepository(String repositoryUri) {
        if (repositoryUri == null) {
            throw new IllegalStateException(MISSING_AUTHORITY_MESSAGE);
        }
        String trimmed = trimAuthority(repositoryUri);
        if (trimmed.isEmpty()
                || trimmed.contains(UNRESOLVED_PLACEHOLDER)
                || isRetiredDemoRemote(trimmed)) {
            throw new IllegalStateException(MISSING_AUTHORITY_MESSAGE);
        }
        if (REQUEST_TEMPLATE.matcher(trimmed).find()) {
            throw new IllegalStateException(REQUEST_TEMPLATE_MESSAGE);
        }
    }

    /**
     * Removes leading and trailing Unicode space and format padding.
     *
     * <p>{@link String#isBlank()} and {@link String#strip()} miss NBSP and
     * zero-width padding, which would otherwise pass as a non-empty URI.</p>
     *
     * @param repositoryUri raw bound value
     * @return value with ignorable padding removed from both ends
     */
    static String trimAuthority(String repositoryUri) {
        int start = 0;
        int end = repositoryUri.length();
        while (start < end && isIgnorablePad(repositoryUri.codePointAt(start))) {
            start += Character.charCount(repositoryUri.codePointAt(start));
        }
        while (end > start) {
            int codePoint = repositoryUri.codePointBefore(end);
            if (!isIgnorablePad(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return repositoryUri.substring(start, end);
    }

    static boolean isRetiredDemoRemote(String repositoryUri) {
        if (SCP_DEMO.matcher(repositoryUri).find()) {
            return true;
        }
        try {
            URI uri = URI.create(repositoryUri);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null) {
                return false;
            }
            return RETIRED_DEMO_HOST.equalsIgnoreCase(host) && isRetiredDemoPath(path);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isRetiredDemoPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return RETIRED_DEMO_PATH.equals(normalized);
    }

    private static boolean isIgnorablePad(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0x200B
                || codePoint == 0xFEFF;
    }
}
