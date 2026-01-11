package com.xtrmetl.cdc.config;

import com.xtrmetl.cdc.util.ValidationUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ReplicaJdbcTemplateConfig {

    @Bean(name = "replicaDataSource", destroyMethod = "close")
    public HikariDataSource replicaDataSource(Environment environment) {
        String host = ValidationUtils.requireValidHost(
                environment.getRequiredProperty("REPLICA_PGHOST"),
                "REPLICA_PGHOST"
        );
        String port = ValidationUtils.requireValidPort(
                environment.getProperty("REPLICA_PGPORT", "5432"),
                "REPLICA_PGPORT"
        );
        String database = ValidationUtils.requireValidIdentifier(
                environment.getRequiredProperty("REPLICA_PGDATABASE"),
                "REPLICA_PGDATABASE"
        );
        String username = environment.getRequiredProperty("REPLICA_PGUSER");
        String password = environment.getRequiredProperty("REPLICA_PGPASSWORD");

        HikariConfig config = new HikariConfig();
        config.setPoolName("cdc-replica-pool");
        // Default -1 disables fail-fast startup checks (allows the service to start even if the replica DB is temporarily unavailable).
        // Set REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS (e.g. 30000) to fail fast during startup instead.
        String initializationFailTimeoutValue =
                environment.getProperty("REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS", "-1");
        long initializationFailTimeout;
        try {
            initializationFailTimeout = Long.parseLong(initializationFailTimeoutValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid value for REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS: " + initializationFailTimeoutValue,
                    e
            );
        }
        config.setInitializationFailTimeout(initializationFailTimeout);
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, database));
        config.setUsername(username);
        config.setPassword(password);

        return new HikariDataSource(config);
    }

    @Bean(name = "replicaJdbcTemplate")
    public JdbcTemplate replicaJdbcTemplate(@Qualifier("replicaDataSource") @NonNull DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
