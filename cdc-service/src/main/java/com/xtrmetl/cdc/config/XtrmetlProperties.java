package com.xtrmetl.cdc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xtrmetl")
public class XtrmetlProperties {

    private final Cdc cdc = new Cdc();
    private final Replica replica = new Replica();

    public Cdc getCdc() {
        return cdc;
    }

    public Replica getReplica() {
        return replica;
    }

    public static class Cdc {
        private boolean autostart = true;
        /**
         * When true, map each Debezium event to {@code CanonicalChangeRecord} for validation
         * counters (live Kafka payload remains raw Debezium JSON).
         */
        private boolean canonicalMapEnabled = false;
        /**
         * Declared multi-source list for any-to-any roadmap. Live engine still runs a single
         * Postgres Debezium path in {@code CdcService}; extra enabled sources are reported
         * but not started.
         */
        private java.util.List<Source> sources = new java.util.ArrayList<>(java.util.List.of(
                defaultPostgresSource()
        ));

        public boolean isAutostart() {
            return autostart;
        }

        public void setAutostart(boolean autostart) {
            this.autostart = autostart;
        }

        public boolean isCanonicalMapEnabled() {
            return canonicalMapEnabled;
        }

        public void setCanonicalMapEnabled(boolean canonicalMapEnabled) {
            this.canonicalMapEnabled = canonicalMapEnabled;
        }

        public java.util.List<Source> getSources() {
            return sources;
        }

        public void setSources(java.util.List<Source> sources) {
            this.sources = sources != null ? sources : new java.util.ArrayList<>();
        }

        private static Source defaultPostgresSource() {
            Source source = new Source();
            source.setId("pg-main");
            source.setType("postgres-debezium");
            source.setEnabled(true);
            return source;
        }
    }

    public static class Source {
        private String id = "pg-main";
        private String type = "postgres-debezium";
        private boolean enabled = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Replica {
        private boolean enabled = false;
        private String groupId = "xtrmetl-cdc-replica";
        private String topicPattern = "xtrmetl-cdc\\..*";
        /**
         * Comma-separated table names eligible for JDBC replica apply.
         * Tables must use the {@code (id BIGINT PK, data TEXT)} shape used by {@code processed_data}.
         */
        private String tables = "processed_data";
        private boolean ddlEnabled = false;
        private String ddlValidationMode = "whitelist";
        private String ddlAllowedPrefixes = "CREATE TABLE,ALTER TABLE,CREATE INDEX";
        private String ddlBlockedPrefixes = "DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE";
        private final Kafka kafka = new Kafka();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getTopicPattern() {
            return topicPattern;
        }

        public void setTopicPattern(String topicPattern) {
            this.topicPattern = topicPattern;
        }

        public String getTables() {
            return tables;
        }

        public void setTables(String tables) {
            this.tables = tables;
        }

        public boolean isDdlEnabled() {
            return ddlEnabled;
        }

        public void setDdlEnabled(boolean ddlEnabled) {
            this.ddlEnabled = ddlEnabled;
        }

        public String getDdlValidationMode() {
            return ddlValidationMode;
        }

        public void setDdlValidationMode(String ddlValidationMode) {
            this.ddlValidationMode = ddlValidationMode;
        }

        public String getDdlAllowedPrefixes() {
            return ddlAllowedPrefixes;
        }

        public void setDdlAllowedPrefixes(String ddlAllowedPrefixes) {
            this.ddlAllowedPrefixes = ddlAllowedPrefixes;
        }

        public String getDdlBlockedPrefixes() {
            return ddlBlockedPrefixes;
        }

        public void setDdlBlockedPrefixes(String ddlBlockedPrefixes) {
            this.ddlBlockedPrefixes = ddlBlockedPrefixes;
        }

        public Kafka getKafka() {
            return kafka;
        }

        public static class Kafka {
            private int concurrency = 1;
            private long retryBackoffMs = 1000L;
            private long retryMaxAttempts = 30L;

            public int getConcurrency() {
                return concurrency;
            }

            public void setConcurrency(int concurrency) {
                this.concurrency = concurrency;
            }

            public long getRetryBackoffMs() {
                return retryBackoffMs;
            }

            public void setRetryBackoffMs(long retryBackoffMs) {
                this.retryBackoffMs = retryBackoffMs;
            }

            public long getRetryMaxAttempts() {
                return retryMaxAttempts;
            }

            public void setRetryMaxAttempts(long retryMaxAttempts) {
                this.retryMaxAttempts = retryMaxAttempts;
            }
        }
    }
}
