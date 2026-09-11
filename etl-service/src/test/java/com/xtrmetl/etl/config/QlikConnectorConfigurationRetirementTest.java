package com.xtrmetl.etl.config;

import com.xtrmetl.etl.connector.ConnectorProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards removal of Qlik row-write configuration from the production connector surface while the
 * real Qlik integration remains intentionally unimplemented.
 */
class QlikConnectorConfigurationRetirementTest {

    @Test
    void productionAliasRegistryDoesNotAdvertiseQlikRowWriteEnablement() {
        assertFalse(
                MightyEtlConfigAliasEnvironmentPostProcessor.RELATIVE_KEYS
                        .contains("connectors.qlik-sense.enabled")
        );
    }

    @Test
    void productionConnectorPropertiesDoNotBindQlikRowWriteCredentials() {
        assertThrows(NoSuchMethodException.class, () -> ConnectorProperties.class.getMethod("getQlikSense"));
        assertFalse(
                Arrays.stream(ConnectorProperties.class.getDeclaredClasses())
                        .anyMatch(type -> type.getSimpleName().equals("QlikSenseProps"))
        );

        ConnectorProperties properties = new ConnectorProperties();
        assertFalse(properties.isEnabled("qlik-sense"));
        assertTrue(properties.configMap("qlik-sense").isEmpty());
    }
}
