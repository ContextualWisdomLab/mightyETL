package com.xtrmetl.cdc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MightyEtlConfigAliasEnvironmentPostProcessorTest {

    @Test
    void modernPrefixWinsAndMirrorsToLegacy() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("mightyetl.cdc.autostart", "false");
        env.setProperty("xtrmetl.cdc.autostart", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("false", aliases.get("xtrmetl.cdc.autostart"));
        assertFalse(aliases.containsKey("mightyetl.cdc.autostart"));
    }

    @Test
    void legacyOnlyIsMirroredToModern() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("xtrmetl.replica.enabled", "true");

        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);

        assertEquals("true", aliases.get("mightyetl.replica.enabled"));
        assertFalse(aliases.containsKey("xtrmetl.replica.enabled"));
    }

    @Test
    void emptyWhenNeitherPrefixSet() {
        MockEnvironment env = new MockEnvironment();
        Map<String, Object> aliases = MightyEtlConfigAliasEnvironmentPostProcessor.buildAliases(env);
        assertTrue(aliases.isEmpty());
    }
}
