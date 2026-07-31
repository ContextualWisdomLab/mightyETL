package com.xtrmetl.etl.connector;

/**
 * Scaffold for Snowflake table loads (JDBC / Snowpipe — not wired).
 *
 * @see docs/connectors/snowflake.md
 */
public final class SnowflakeTargetConnector extends AbstractScaffoldTargetConnector {

    public SnowflakeTargetConnector() {
        super("snowflake", "Snowflake");
    }
}
