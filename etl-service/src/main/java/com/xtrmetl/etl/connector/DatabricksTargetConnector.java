package com.xtrmetl.etl.connector;

import java.util.List;
import java.util.Map;

/**
 * Databricks Unity Catalog / SQL warehouse target surface.
 *
 * <p>Ships config contract + validation + catalog hooks. No JDBC/SDK on the classpath;
 * {@link #write} always refuses (SCAFFOLD).</p>
 *
 * @see docs/connectors/databricks.md
 */
public final class DatabricksTargetConnector extends AbstractScaffoldTargetConnector {

    public DatabricksTargetConnector() {
        super(
                "databricks",
                "Databricks",
                List.of("host", "http-path", "token", "catalog", "schema", "table"),
                List.of("write-mode"),
                Map.of(
                        "client", "databricks-sql-jdbc-or-statement-api",
                        "plannedPath", "batch upsert via SQL warehouse / Statement Execution API",
                        "driverOnClasspath", false,
                        "docs", "docs/connectors/databricks.md"
                )
        );
    }
}
