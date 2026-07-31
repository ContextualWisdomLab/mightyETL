package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scaffold for SQL Server CDC via Debezium. Not on the classpath / not started.
 */
@Component
public final class SqlServerDebeziumCdcSource extends AbstractScaffoldCdcSource {

    public static final String ID = "sqlserver-debezium";

    public SqlServerDebeziumCdcSource() {
        super(ID, "SQL Server (Debezium scaffold)", "debezium-embedded", Set.of("sqlserver"));
    }
}
