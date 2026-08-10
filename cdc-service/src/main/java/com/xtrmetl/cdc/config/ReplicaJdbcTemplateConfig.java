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

import javax.sql.DataSource;

/**
 * Creates the optional PostgreSQL replica JDBC infrastructure from deployment-owned configuration.
 *
 * <p>The configuration validates bounded connection coordinates before constructing the data source
 * and reports invalid configuration by stable key rather than republishing rejected raw values or
 * parser diagnostics into startup logs and support surfaces.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ReplicaJdbcTemplateConfig {

    /**
     * Creates the Hikari data source used by CDC replica application.
     *
     * @param environment Spring environment containing the deployment-owned replica settings
     * @return configured replica data source
     * @throws IllegalStateException when a required replica setting is absent or invalid
     */
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
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid value for REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS");
        }
        config.setInitializationFailTimeout(initializationFailTimeout);
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, database));
        config.setUsername(username);
        config.setPassword(password);

        return new HikariDataSource(config);
    }

    /**
     * Creates the JDBC template that applies validated CDC records to the configured replica.
     *
     * @param dataSource validated replica data source
     * @return JDBC template bound to the replica data source
     */
    @Bean(name = "replicaJdbcTemplate")
    public JdbcTemplate replicaJdbcTemplate(@Qualifier("replicaDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}