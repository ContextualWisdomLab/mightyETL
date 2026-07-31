package com.xtrmetl.etl.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Target connector enable flags (scaffolds). Enabling a scaffold without a real
 * implementation is refused by {@link TargetConnectorDispatcher}.
 */
@ConfigurationProperties(prefix = "xtrmetl.connectors")
public class ConnectorProperties {

    private final ConnectorFlag databricks = new ConnectorFlag();
    private final ConnectorFlag snowflake = new ConnectorFlag();
    private final ConnectorFlag qlikSense = new ConnectorFlag();

    public ConnectorFlag getDatabricks() {
        return databricks;
    }

    public ConnectorFlag getSnowflake() {
        return snowflake;
    }

    public ConnectorFlag getQlikSense() {
        return qlikSense;
    }

    public boolean isEnabled(String connectorId) {
        return switch (connectorId) {
            case "databricks" -> databricks.isEnabled();
            case "snowflake" -> snowflake.isEnabled();
            case "qlik-sense" -> qlikSense.isEnabled();
            default -> false;
        };
    }

    public static class ConnectorFlag {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
