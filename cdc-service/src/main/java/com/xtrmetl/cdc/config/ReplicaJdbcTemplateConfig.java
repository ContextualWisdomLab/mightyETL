package com.xtrmetl.cdc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PreDestroy;

@Configuration
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ReplicaJdbcTemplateConfig {

    private HikariDataSource replicaDataSource;

    @Bean(name = "replicaJdbcTemplate")
    public JdbcTemplate replicaJdbcTemplate() {
        String host = requireEnv("REPLICA_PGHOST");
        String port = getEnv("REPLICA_PGPORT", "5432");
        String database = requireEnv("REPLICA_PGDATABASE");
        String username = requireEnv("REPLICA_PGUSER");
        String password = requireEnv("REPLICA_PGPASSWORD");

        HikariConfig config = new HikariConfig();
        config.setPoolName("cdc-replica-pool");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);

        this.replicaDataSource = new HikariDataSource(config);

        return new JdbcTemplate(replicaDataSource);
    }

    @PreDestroy
    public void shutdownReplicaDataSource() {
        if (replicaDataSource != null) {
            replicaDataSource.close();
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
