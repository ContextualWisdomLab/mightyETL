package com.xtrmetl.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the runnable Config Server never invents a remote Git repository authority.
 */
class ConfigServerRepositoryAuthorityLiveTest {

    @Test
    void missingRepositoryAuthorityFailsClosedWithoutDemoRemote() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(
                applicationYaml.contains("uri: ${CONFIG_REPO_URI}"),
                "Config Server must require an explicitly supplied CONFIG_REPO_URI"
        );
        assertFalse(
                applicationYaml.contains("your-repo/config-repo.git"),
                "A demo remote must never become a runnable fallback"
        );
        assertFalse(
                applicationYaml.contains("CONFIG_REPO_URI:https://"),
                "Missing repository authority must fail before implicit remote Git access"
        );
    }
}
