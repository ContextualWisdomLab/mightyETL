package com.xtrmetl.cdc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PreDestroy;

@Configuration
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class ReplicaJdbcTemplateConfig {

    private HikariDataSource replicaDataSource;

    @Bean(name = "replicaJdbcTemplate")
    public JdbcTemplate replicaJdbcTemplate(Environment environment) {
        String host = environment.getRequiredProperty("REPLICA_PGHOST");
        String port = environment.getProperty("REPLICA_PGPORT", "5432");
        String database = environment.getRequiredProperty("REPLICA_PGDATABASE");
        String username = environment.getRequiredProperty("REPLICA_PGUSER");
        String password = environment.getRequiredProperty("REPLICA_PGPASSWORD");

        HikariConfig config = new HikariConfig();
        config.setPoolName("cdc-replica-pool");
        config.setInitializationFailTimeout(-1);
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
