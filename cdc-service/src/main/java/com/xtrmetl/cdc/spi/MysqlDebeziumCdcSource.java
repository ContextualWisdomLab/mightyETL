package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scaffold for MySQL binlog CDC via Debezium. Not on the classpath / not started.
 */
@Component
public final class MysqlDebeziumCdcSource extends AbstractScaffoldCdcSource {

    public static final String ID = "mysql-debezium";

    public MysqlDebeziumCdcSource() {
        super(ID, "MySQL (Debezium scaffold)", "debezium-embedded", Set.of("mysql"));
    }
}
