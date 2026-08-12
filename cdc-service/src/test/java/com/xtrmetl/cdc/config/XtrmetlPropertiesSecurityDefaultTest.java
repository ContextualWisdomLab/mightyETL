package com.xtrmetl.cdc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the secure standalone default exposed by the CDC replica configuration object.
 */
class XtrmetlPropertiesSecurityDefaultTest {

    @Test
    void replicaDdlRemainsDisabledAndUsesWhitelistValidationByDefault() {
        XtrmetlProperties properties = new XtrmetlProperties();

        assertFalse(properties.getReplica().isDdlEnabled(), "DDL replication must remain disabled by default");
        assertEquals(
                "whitelist",
                properties.getReplica().getDdlValidationMode(),
                "the Java configuration object must match the deployable and metadata secure default"
        );
    }
}
