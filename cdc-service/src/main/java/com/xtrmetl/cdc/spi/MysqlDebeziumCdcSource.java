package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scaffold for MySQL binlog CDC via Debezium. Not on the classpath / not started.
 */
@Component
public final class MysqlDebeziumCdcSource extends AbstractScaffoldCdcSource {

    public static final String SOURCE_ID = "mysql-debezium";

    /**
     * @deprecated compatibility alias; organization-owned callers use {@link #SOURCE_ID}
     */
    @Deprecated(forRemoval = false)
    public static final String ID = SOURCE_ID;

    public MysqlDebeziumCdcSource() {
        super(SOURCE_ID, "MySQL (Debezium scaffold)", "debezium-embedded", Set.of("mysql"));
    }
}
