package com.xtrmetl.etl.connector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards bounded-context-specific target identity on the organization-owned ETL connector SPI.
 */
class TargetConnectorSemanticIdentityTest {

    @Test
    @SuppressWarnings("deprecation")
    void targetConnectorExposesSemanticIdentityAndPreservesLegacyAlias() {
        TargetConnector databricksTargetConnector = new DatabricksTargetConnector();

        assertEquals("databricks", databricksTargetConnector.targetId());
        assertEquals(databricksTargetConnector.targetId(), databricksTargetConnector.id());
    }
}
