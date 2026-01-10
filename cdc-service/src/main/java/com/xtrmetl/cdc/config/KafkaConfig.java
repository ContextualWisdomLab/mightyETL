package com.xtrmetl.cdc.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka-related configuration for {@code cdc-service}.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaListenerErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        @SuppressWarnings("unchecked")
        KafkaOperations<Object, Object> operations = (KafkaOperations<Object, Object>) (KafkaOperations<?, ?>) kafkaTemplate;

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                operations,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaListenerErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Commit offsets only after a record has been successfully applied to the replica DB.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaListenerErrorHandler);
        return factory;
    }
}
