package com.xtrmetl.cdc.spi;

import java.util.Set;

/**
 * Reference scaffold for SQL Server CDC via Debezium.
 *
 * <p>This type is intentionally not registered as a Spring component while its runtime start path
 * remains scaffold-only. Construct it explicitly only for design/reference validation until a
 * maintained SQL Server Debezium implementation and operational evidence exist.
 */
public final class SqlServerDebeziumCdcSource extends AbstractScaffoldCdcSource {

    public static final String ID = "sqlserver-debezium";

    public SqlServerDebeziumCdcSource() {
        super(ID, "SQL Server (Debezium scaffold)", "debezium-embedded", Set.of("sqlserver"));
    }
}
