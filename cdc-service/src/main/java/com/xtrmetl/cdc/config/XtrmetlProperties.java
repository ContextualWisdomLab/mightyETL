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

        public boolean isAutostart() {
            return autostart;
        }

        public void setAutostart(boolean autostart) {
            this.autostart = autostart;
        }
    }

    public static class Replica {
        private boolean enabled = false;
        private String groupId = "xtrmetl-cdc-replica";
        private String topicPattern = "xtrmetl-cdc\\..*";
        private boolean ddlEnabled = false;
        private String ddlValidationMode = "none";
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
