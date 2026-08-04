package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that the Spring proxy rolls back earlier writes when a later record fails at the database.
 */
@SpringJUnitConfig(EtlServiceTransactionIntegrationTest.TestConfiguration.class)
class EtlServiceTransactionIntegrationTest {

    private final EtlService etlService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlServiceTransactionIntegrationTest(EtlService etlService, JdbcTemplate jdbcTemplate) {
        this.etlService = etlService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createTargetTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS processed_data");
        jdbcTemplate.execute("""
                CREATE TABLE processed_data (
                    processed_data_key UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                    data VARCHAR(4096) NOT NULL,
                    CONSTRAINT valid_processed_data CHECK (data NOT LIKE '%NAME:FAIL,%')
                )
                """);
    }

    @Test
    void commitsEveryRecordWhenTheBatchSucceeds() {
        etlService.processData("""
                [
                  {"id":"record_alpha","name":"accepted"},
                  {"id":"record_beta","name":"accepted"}
                ]
                """);

        assertEquals(2, countRows());
    }

    @Test
    void rollsBackEarlierWritesWhenALaterRecordViolatesTheTargetConstraint() {
        String batch = """
                [
                  {"id":"record_alpha","name":"accepted"},
                  {"id":"record_beta","name":"fail"}
                ]
                """;

        assertThrows(RuntimeException.class, () -> etlService.processData(batch));

        assertEquals(0, countRows());
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_data",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    /**
     * Minimal transaction-enabled Spring context without the service's discovery/network stack.
     */
    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EtlBatchProperties etlBatchProperties() {
            return new EtlBatchProperties();
        }

        @Bean
        EtlService etlService(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                EtlBatchProperties properties
        ) {
            return new EtlService(jdbcTemplate, objectMapper, properties);
        }
    }
}
