package com.xtrmetl.etl.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Target connector enable flags and binding surface for warehouse/BI scaffolds.
 * Enabling a scaffold without a real implementation is refused by {@link TargetConnectorDispatcher}.
 *
 * <p>Prefix: {@code xtrmetl.connectors.*} (dual-read from {@code mightyetl.connectors.*}).</p>
 */
@ConfigurationProperties(prefix = "xtrmetl.connectors")
public class ConnectorProperties {

    private final DatabricksProps databricks = new DatabricksProps();
    private final SnowflakeProps snowflake = new SnowflakeProps();
    private final QlikSenseProps qlikSense = new QlikSenseProps();

    public DatabricksProps getDatabricks() {
        return databricks;
    }

    public SnowflakeProps getSnowflake() {
        return snowflake;
    }

    public QlikSenseProps getQlikSense() {
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

    /**
     * Flatten bound properties into a config map suitable for {@link TargetConnector#validate}.
     * Blank values are omitted so validation can detect missing required keys.
     */
    public Map<String, String> configMap(String connectorId) {
        return switch (connectorId) {
            case "databricks" -> databricks.toConfigMap();
            case "snowflake" -> snowflake.toConfigMap();
            case "qlik-sense" -> qlikSense.toConfigMap();
            default -> Map.of();
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

    public static class DatabricksProps extends ConnectorFlag {
        private String host = "";
        private String httpPath = "";
        private String token = "";
        private String catalog = "";
        private String schema = "";
        private String table = "";
        private String writeMode = "append";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getHttpPath() {
            return httpPath;
        }

        public void setHttpPath(String httpPath) {
            this.httpPath = httpPath;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getCatalog() {
            return catalog;
        }

        public void setCatalog(String catalog) {
            this.catalog = catalog;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getWriteMode() {
            return writeMode;
        }

        public void setWriteMode(String writeMode) {
            this.writeMode = writeMode;
        }

        Map<String, String> toConfigMap() {
            Map<String, String> map = new LinkedHashMap<>();
            putIfPresent(map, "host", host);
            putIfPresent(map, "http-path", httpPath);
            putIfPresent(map, "token", token);
            putIfPresent(map, "catalog", catalog);
            putIfPresent(map, "schema", schema);
            putIfPresent(map, "table", table);
            putIfPresent(map, "write-mode", writeMode);
            return map;
        }
    }

    public static class SnowflakeProps extends ConnectorFlag {
        private String account = "";
        private String warehouse = "";
        private String database = "";
        private String schema = "";
        private String user = "";
        private String password = "";
        private String privateKey = "";
        private String role = "";
        private String table = "";
        private String mergeKeys = "";

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public String getWarehouse() {
            return warehouse;
        }

        public void setWarehouse(String warehouse) {
            this.warehouse = warehouse;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getMergeKeys() {
            return mergeKeys;
        }

        public void setMergeKeys(String mergeKeys) {
            this.mergeKeys = mergeKeys;
        }

        Map<String, String> toConfigMap() {
            Map<String, String> map = new LinkedHashMap<>();
            putIfPresent(map, "account", account);
            putIfPresent(map, "warehouse", warehouse);
            putIfPresent(map, "database", database);
            putIfPresent(map, "schema", schema);
            putIfPresent(map, "user", user);
            putIfPresent(map, "password", password);
            putIfPresent(map, "private-key", privateKey);
            putIfPresent(map, "role", role);
            putIfPresent(map, "table", table);
            putIfPresent(map, "merge-keys", mergeKeys);
            return map;
        }
    }

    public static class QlikSenseProps extends ConnectorFlag {
        private String tenantUrl = "";
        private String apiKey = "";
        private String appId = "";
        private String mode = "reload-only";

        public String getTenantUrl() {
            return tenantUrl;
        }

        public void setTenantUrl(String tenantUrl) {
            this.tenantUrl = tenantUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        Map<String, String> toConfigMap() {
            Map<String, String> map = new LinkedHashMap<>();
            putIfPresent(map, "tenant-url", tenantUrl);
            putIfPresent(map, "api-key", apiKey);
            putIfPresent(map, "app-id", appId);
            putIfPresent(map, "mode", mode);
            return map;
        }
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
