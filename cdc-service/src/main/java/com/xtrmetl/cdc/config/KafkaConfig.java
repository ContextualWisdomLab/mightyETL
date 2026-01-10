package com.xtrmetl.cdc.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka-related configuration for {@code cdc-service}.
 *
 * Intentionally empty: we rely on Spring Boot's Kafka auto-configuration to provide
 * {@code KafkaTemplate} and related beans from {@code spring.kafka.*} properties.
 */
@Configuration
public class KafkaConfig {}

