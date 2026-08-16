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
        assertThrows(
                IllegalStateException.class,
                () -> ConfigServerRepositoryAuthority.requireExplicitRepository(
                        "https://github.com/your-repo/config-repo.git"
                )
        );
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
