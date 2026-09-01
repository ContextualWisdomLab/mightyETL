package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scaffold for SQL Server CDC via Debezium. Not on the classpath / not started.
 */
@Component
public final class SqlServerDebeziumCdcSource extends AbstractScaffoldCdcSource {

    public static final String SOURCE_ID = "sqlserver-debezium";

    /**
     * @deprecated compatibility alias; organization-owned callers use {@link #SOURCE_ID}
     */
    @Deprecated(forRemoval = false)
    public static final String ID = SOURCE_ID;

    public SqlServerDebeziumCdcSource() {
        super(SOURCE_ID, "SQL Server (Debezium scaffold)", "debezium-embedded", Set.of("sqlserver"));
    }
}
