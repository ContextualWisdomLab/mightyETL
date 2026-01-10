package com.xtrmetl.cdc.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class ReplicaJdbcTemplateConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ReplicaJdbcTemplateConfig.class);

    @Test
    void createsJdbcTemplateWithConfiguredReplicaDataSource() {
        AtomicReference<HikariDataSource> hikariRef = new AtomicReference<>();

        contextRunner
                .withPropertyValues(
                        "xtrmetl.replica.enabled=true",
                        "REPLICA_PGHOST=replica-host",
                        "REPLICA_PGPORT=15432",
                        "REPLICA_PGDATABASE=xtrmetl",
                        "REPLICA_PGUSER=xtrmetl_user",
                        "REPLICA_PGPASSWORD=xtrmetl_password"
                )
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.containsBean("replicaJdbcTemplate"));

                    JdbcTemplate jdbcTemplate = context.getBean("replicaJdbcTemplate", JdbcTemplate.class);
                    DataSource dataSource = jdbcTemplate.getDataSource();
                    assertNotNull(dataSource);

                    HikariDataSource hikari = assertInstanceOf(HikariDataSource.class, dataSource);
                    assertEquals("jdbc:postgresql://replica-host:15432/xtrmetl", hikari.getJdbcUrl());
                    assertEquals("xtrmetl_user", hikari.getUsername());
                    assertEquals("xtrmetl_password", hikari.getPassword());
                    assertEquals("cdc-replica-pool", hikari.getPoolName());
                    assertEquals(-1L, hikari.getInitializationFailTimeout());

                    hikariRef.set(hikari);
                });

        assertTrue(hikariRef.get().isClosed());
    }

    @Test
    void defaultsPortWhenReplicaPortIsNotProvided() {
        contextRunner
                .withPropertyValues(
                        "xtrmetl.replica.enabled=true",
                        "REPLICA_PGHOST=replica-host",
                        "REPLICA_PGDATABASE=xtrmetl",
                        "REPLICA_PGUSER=xtrmetl_user",
                        "REPLICA_PGPASSWORD=xtrmetl_password"
                )
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    JdbcTemplate jdbcTemplate = context.getBean("replicaJdbcTemplate", JdbcTemplate.class);
                    HikariDataSource hikari = assertInstanceOf(HikariDataSource.class, jdbcTemplate.getDataSource());
                    assertEquals("jdbc:postgresql://replica-host:5432/xtrmetl", hikari.getJdbcUrl());
                });
    }

    @Test
    void failsFastWhenRequiredReplicaEnvIsMissing() {
        contextRunner
                .withPropertyValues("xtrmetl.replica.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
