package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards bounded-context-specific identity accessors on the organization-owned CDC SPI.
 */
class CdcConnectorSemanticIdentityTest {

    @Test
    @SuppressWarnings("deprecation")
    void sourceConnectorExposesSemanticIdentityAndPreservesLegacyAlias() {
        CdcSourceConnector sourceConnector = new PostgresDebeziumCdcSource();

        assertEquals(PostgresDebeziumCdcSource.SOURCE_ID, sourceConnector.sourceId());
        assertEquals(sourceConnector.sourceId(), sourceConnector.id());
        assertEquals(PostgresDebeziumCdcSource.SOURCE_ID, PostgresDebeziumCdcSource.ID);
    }

    @Test
    @SuppressWarnings("deprecation")
    void targetConnectorsExposeSemanticIdentityAndPreserveLegacyAliases() {
        CdcTargetConnector kafkaTargetConnector = new KafkaCdcTargetConnector();
        CdcTargetConnector jdbcTargetConnector = new JdbcReplicaCdcTargetConnector();

        assertEquals(KafkaCdcTargetConnector.TARGET_ID, kafkaTargetConnector.targetId());
        assertEquals(JdbcReplicaCdcTargetConnector.TARGET_ID, jdbcTargetConnector.targetId());
        assertEquals(kafkaTargetConnector.targetId(), kafkaTargetConnector.id());
        assertEquals(jdbcTargetConnector.targetId(), jdbcTargetConnector.id());
        assertEquals(KafkaCdcTargetConnector.TARGET_ID, KafkaCdcTargetConnector.ID);
        assertEquals(JdbcReplicaCdcTargetConnector.TARGET_ID, JdbcReplicaCdcTargetConnector.ID);
    }
}
