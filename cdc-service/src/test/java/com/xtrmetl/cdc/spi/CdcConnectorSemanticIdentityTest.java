package com.xtrmetl.cdc.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards bounded-context-specific identity accessors on the organization-owned CDC SPI.
 */
class CdcConnectorSemanticIdentityTest {

    @Test
    void sourceConnectorExposesSourceIdentityBySemanticName() {
        CdcSourceConnector sourceConnector = new PostgresDebeziumCdcSource();

        assertEquals("postgres-debezium", sourceConnector.sourceId());
    }

    @Test
    void targetConnectorsExposeTargetIdentityBySemanticName() {
        CdcTargetConnector kafkaTargetConnector = new KafkaCdcTargetConnector();
        CdcTargetConnector jdbcTargetConnector = new JdbcReplicaCdcTargetConnector();

        assertEquals("kafka", kafkaTargetConnector.targetId());
        assertEquals("jdbc-replica", jdbcTargetConnector.targetId());
    }
}
