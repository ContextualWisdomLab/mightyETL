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
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository("\u00A0\u200B")
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository("${CONFIG_REPO_URI}")
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository("${CONFIG_REPO_URI:}")
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "${CONFIG_REPO_URI:https://evil.example/config.git}"
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "https://github.com/your-repo/config-repo.git"
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "https://github.com/your-repo/config-repo"
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "HTTPS://GITHUB.COM/YOUR-REPO/CONFIG-REPO.GIT"
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "git@github.com:your-repo/config-repo.git"
                )
        );
    }

    @Test
    void rejectsRequestTemplatedGitLocations() {
        assertEquals(
                ConfigServerRepositoryAuthority.REQUEST_TEMPLATE_MESSAGE,
                assertThrows(
                        IllegalStateException.class,
                        () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                                "https://github.com/{application}/config.git"
                        )
                ).getMessage()
        );
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "https://git.example.internal/{profile}/{label}.git"
                )
        );
    }

    @Test
    void acceptsExplicitHttpsSshFileAndNonDemoNestedPaths() {
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "https://git.example.internal/config-repo.git"
        ));
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "  https://git.example.internal/config-repo.git  "
        ));
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "ssh://git@git.example.internal/config-repo.git"
        ));
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "file:/opt/reviewed-config-repo"
        ));
        assertDoesNotThrow(() -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                "https://github.com/acme/your-repo/config-repo.git"
        ));
    }
}
