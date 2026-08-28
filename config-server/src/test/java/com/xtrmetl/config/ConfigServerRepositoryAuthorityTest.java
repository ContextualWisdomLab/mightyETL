package com.xtrmetl.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the destination-authority predicate against realistic operator values.
 */
class ConfigServerRepositoryAuthorityTest {

    @Test
    void rejectsMissingBlankUnresolvedAndDemoRemotes() {
        assertEquals(
                ConfigServerRepositoryAuthority.MISSING_AUTHORITY_MESSAGE,
                assertThrows(
                        IllegalStateException.class,
                        () -> ConfigServerRepositoryAuthority.requireExplicitRepository(null)
                ).getMessage()
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository("")
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository("   ")
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository("${CONFIG_REPO_URI}")
        );
        for (String demoRemote : new String[] {
                "https://github.com/your-repo/config-repo.git",
                "https://github.com/your-repo/config-repo",
                "HTTPS://GITHUB.COM/YOUR-REPO/CONFIG-REPO.GIT",
                "https://github.com/your-repo/config-repo/"
        }) {
            assertThrows(
                    IllegalStateException.class,
                    () -> ConfigServerRepositoryAuthority.requireExplicitRepository(demoRemote),
                    () -> "Retired demo repository must be rejected: " + demoRemote
            );
        }
    }

    @Test
    void demoRepositoryMatchingDoesNotRejectUnrelatedPaths() {
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "https://github.com/acme/your-repo/config-repo.git"
        ));
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "https://github.com/your-repo/config-repo-backup.git"
        ));
    }

    @Test
    void acceptsExplicitHttpsAndFileAuthorities() {
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "https://git.example.internal/config-repo.git"
        ));
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "file:/opt/reviewed-config-repo"
        ));
    }
}
