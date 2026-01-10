package com.xtrmetl.cdc.config;

import com.xtrmetl.cdc.util.EnvUtils;
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
        String host = EnvUtils.requireEnv("REPLICA_PGHOST");
        String port = EnvUtils.getEnv("REPLICA_PGPORT", "5432");
        String database = EnvUtils.requireEnv("REPLICA_PGDATABASE");
        String username = EnvUtils.requireEnv("REPLICA_PGUSER");
        String password = EnvUtils.requireEnv("REPLICA_PGPASSWORD");

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
}
