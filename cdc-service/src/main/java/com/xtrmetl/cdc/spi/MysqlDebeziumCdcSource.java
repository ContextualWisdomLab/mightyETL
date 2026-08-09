package com.xtrmetl.cdc.spi;

import java.util.Set;

/**
 * Non-production reference scaffold for a future MySQL binlog CDC integration via Debezium.
 *
 * <p>This type is intentionally not a Spring component. Production source discovery must not
 * advertise MySQL until the connector dependency, lifecycle, offsets/history, prerequisites,
 * recovery and realistic integration behavior are implemented and verified.</p>
 */
public final class MysqlDebeziumCdcSource extends AbstractScaffoldCdcSource {

    public static final String ID = "mysql-debezium";

    public MysqlDebeziumCdcSource() {
        super(ID, "MySQL (Debezium scaffold)", "debezium-embedded", Set.of("mysql"));
    }
}
