package com.xtrmetl.cdc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka-related configuration for {@code cdc-service}.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);
    private static final int MAX_CONCURRENCY = 32;
    private static final String RETRY_BACKOFF_KEY = "xtrmetl.replica.kafka.retry-backoff-ms";
    private static final String RETRY_MAX_ATTEMPTS_KEY = "xtrmetl.replica.kafka.retry-max-attempts";

    /**
     * Builds the replica-listener error handler with bounded retry configuration and terminal
     * dead-letter recovery.
     *
     * <p>Retry settings are deployment-owned. Negative values are rejected before they reach
     * Spring's {@link FixedBackOff}, while zero remains a valid explicit choice for immediate
     * retry or no retry attempts.</p>
     *
     * @param kafkaTemplate template used to publish exhausted records to the dead-letter topic
     * @param retryBackoffMs fixed delay between retry attempts in milliseconds; must be non-negative
     * @param retryMaxAttempts maximum retry attempts after the original delivery; must be non-negative
     * @return configured listener error handler
     * @throws IllegalArgumentException when either retry setting is negative
     */
    @Bean
    public DefaultErrorHandler kafkaListenerErrorHandler(
            @NonNull KafkaTemplate<String, String> kafkaTemplate,
            @Value("${xtrmetl.replica.kafka.retry-backoff-ms:1000}") long retryBackoffMs,
            @Value("${xtrmetl.replica.kafka.retry-max-attempts:30}") long retryMaxAttempts
    ) {
        requireNonNegative(RETRY_BACKOFF_KEY, retryBackoffMs);
        requireNonNegative(RETRY_MAX_ATTEMPTS_KEY, retryMaxAttempts);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoffMs, retryMaxAttempts)
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            @NonNull ConsumerFactory<String, String> consumerFactory,
            @NonNull DefaultErrorHandler kafkaListenerErrorHandler,
            @Value("${xtrmetl.replica.kafka.concurrency:1}") int concurrency
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        int effectiveConcurrency = Math.max(1, concurrency);
        if (concurrency < 1) {
            log.warn("Invalid xtrmetl.replica.kafka.concurrency={} (must be >= 1); using {}", concurrency, effectiveConcurrency);
        }
        if (effectiveConcurrency > MAX_CONCURRENCY) {
            log.warn(
                    "xtrmetl.replica.kafka.concurrency={} is unusually high; capping to {} to avoid resource exhaustion",
                    effectiveConcurrency,
                    MAX_CONCURRENCY
            );
            effectiveConcurrency = MAX_CONCURRENCY;
        }
        if (effectiveConcurrency > 1) {
            log.warn(
                    "xtrmetl.replica.kafka.concurrency={} may increase out-of-order processing risk between DDL and data events across partitions; prefer concurrency=1 for DDL replication scenarios",
                    effectiveConcurrency
            );
        }
        factory.setConcurrency(effectiveConcurrency);

        // Commit offsets only after a record has been successfully applied to the replica DB.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaListenerErrorHandler);
        return factory;
    }

    private static void requireNonNegative(String key, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be greater than or equal to 0");
        }
    }
}
