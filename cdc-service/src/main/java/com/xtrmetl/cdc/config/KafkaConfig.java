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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka-related configuration for {@code cdc-service}.
 *
 * <p>Dead-letter records retain the failed record and bounded origin/classification metadata needed
 * for authorized recovery, while raw exception messages and stack traces are excluded because they
 * can contain database, provider, credential-adjacent, or other deployment-sensitive diagnostics.</p>
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);
    private static final int MAX_CONCURRENCY = 32;

    /**
     * Creates the replica-consumer error handler with bounded retries and a dead-letter fallback.
     *
     * @param kafkaTemplate Kafka publisher used for dead-letter records
     * @param retryBackoffMs delay between retry attempts in milliseconds
     * @param retryMaxAttempts maximum retry attempts before dead-letter recovery
     * @return configured listener error handler
     */
    @Bean
    public DefaultErrorHandler kafkaListenerErrorHandler(
            @NonNull KafkaTemplate<String, String> kafkaTemplate,
            @Value("${xtrmetl.replica.kafka.retry-backoff-ms:1000}") long retryBackoffMs,
            @Value("${xtrmetl.replica.kafka.retry-max-attempts:30}") long retryMaxAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );
        recoverer.excludeHeader(HeadersToAdd.EX_MSG, HeadersToAdd.EX_STACKTRACE);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoffMs, retryMaxAttempts)
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);
        return errorHandler;
    }

    /**
     * Creates a Kafka listener factory that commits each replica record only after successful handling.
     *
     * @param consumerFactory Kafka consumer factory
     * @param kafkaListenerErrorHandler configured dead-letter/retry handler
     * @param concurrency requested listener concurrency
     * @return bounded listener-container factory
     */
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
}
