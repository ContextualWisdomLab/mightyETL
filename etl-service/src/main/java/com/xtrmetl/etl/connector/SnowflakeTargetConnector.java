package com.xtrmetl.etl.connector;

import java.util.List;
import java.util.Map;

/**
 * Snowflake warehouse target surface.
 *
 * <p>Ships config contract + validation + catalog hooks. No Snowflake JDBC/Snowpipe wired;
 * {@link #write} always refuses (SCAFFOLD).</p>
 *
 * @see docs/connectors/snowflake.md
 */
public final class SnowflakeTargetConnector extends AbstractScaffoldTargetConnector {

    public SnowflakeTargetConnector() {
        super(
                "snowflake",
                "Snowflake",
                List.of("account", "warehouse", "database", "schema", "user", "table"),
                List.of("role", "password", "private-key", "merge-keys"),
                Map.of(
                        "client", "snowflake-jdbc-or-snowpipe",
                        "plannedPath", "stage + MERGE into target tables",
                        "driverOnClasspath", false,
                        "docs", "docs/connectors/snowflake.md"
                )
        );
    }
}
