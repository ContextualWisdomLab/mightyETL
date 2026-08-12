package com.xtrmetl.cdc.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        HikariDataSource hikari = hikariRef.get();
        assertNotNull(hikari);
        assertTrue(hikari.isClosed());
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
                    DataSource dataSource = jdbcTemplate.getDataSource();
                    assertNotNull(dataSource);
                    HikariDataSource hikari = assertInstanceOf(HikariDataSource.class, dataSource);
                    assertEquals("jdbc:postgresql://replica-host:5432/xtrmetl", hikari.getJdbcUrl());
                });
    }

    @Test
    void failsFastWhenRequiredReplicaEnvIsMissing() {
        contextRunner
                .withPropertyValues("xtrmetl.replica.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void failsWithHelpfulMessageWhenInitializationFailTimeoutIsNotANumber() {
        contextRunner
                .withPropertyValues(
                        "xtrmetl.replica.enabled=true",
                        "REPLICA_PGHOST=replica-host",
                        "REPLICA_PGDATABASE=xtrmetl",
                        "REPLICA_PGUSER=xtrmetl_user",
                        "REPLICA_PGPASSWORD=xtrmetl_password",
                        "REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS=not-a-number"
                )
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(failure.getMessage().contains("REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS"));
                });
    }

    @Test
    void invalidInitializationFailTimeoutDiagnosticDoesNotRepublishRejectedValue() {
        String rejectedValue = "not-a-number-Bearer-replica-secret";

        contextRunner
                .withPropertyValues(
                        "xtrmetl.replica.enabled=true",
                        "REPLICA_PGHOST=replica-host",
                        "REPLICA_PGDATABASE=xtrmetl",
                        "REPLICA_PGUSER=xtrmetl_user",
                        "REPLICA_PGPASSWORD=xtrmetl_password",
                        "REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS=" + rejectedValue
                )
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);

                    boolean keyWasReported = false;
                    for (Throwable current = failure; current != null; current = current.getCause()) {
                        String message = current.getMessage();
                        if (message != null) {
                            keyWasReported |= message.contains("REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS");
                            assertFalse(message.contains(rejectedValue));
                        }
                    }
                    assertTrue(keyWasReported);
                });
    }

    @Test
    void invalidInitializationFailTimeoutControlCharactersDoNotReachExceptionChain() {
        String rejectedValue =
                "jdbc:postgresql://replica.internal/app?password=secret\r\nAuthorization: Bearer token";
        MockEnvironment environment = new MockEnvironment()
                .withProperty("REPLICA_PGHOST", "replica-host")
                .withProperty("REPLICA_PGDATABASE", "xtrmetl")
                .withProperty("REPLICA_PGUSER", "xtrmetl_user")
                .withProperty("REPLICA_PGPASSWORD", "xtrmetl_password")
                .withProperty("REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS", rejectedValue);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ReplicaJdbcTemplateConfig().replicaDataSource(environment)
        );

        boolean keyWasReported = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                keyWasReported |= message.contains("REPLICA_HIKARI_INITIALIZATION_FAIL_TIMEOUT_MS");
                assertFalse(message.contains(rejectedValue));
                assertFalse(message.contains("Authorization: Bearer token"));
            }
        }
        assertTrue(keyWasReported);
    }
}
