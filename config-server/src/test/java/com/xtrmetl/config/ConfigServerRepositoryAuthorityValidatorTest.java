package com.xtrmetl.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the startup validator delegates the same fail-closed authority check.
 */
class ConfigServerRepositoryAuthorityValidatorTest {

    @Test
    void afterPropertiesSetRejectsBlankAuthority() {
        ConfigServerRepositoryAuthorityValidator validator =
                new ConfigServerRepositoryAuthorityValidator("");
        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void afterPropertiesSetAcceptsExplicitAuthority() {
        ConfigServerRepositoryAuthorityValidator validator =
                new ConfigServerRepositoryAuthorityValidator("https://git.example.internal/config-repo.git");
        assertDoesNotThrow(validator::afterPropertiesSet);
    }
}
