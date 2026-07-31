package com.xtrmetl.etl.connector;

/**
 * Scaffold for Databricks Unity Catalog / SQL warehouse loads.
 *
 * @see <a href="../../../../../../../../docs/connectors/databricks.md">docs/connectors/databricks.md</a>
 */
public final class DatabricksTargetConnector extends AbstractScaffoldTargetConnector {

    public DatabricksTargetConnector() {
        super("databricks", "Databricks");
    }
}
