package com.xtrmetl.cdc.config;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    /**
     * Creates a KafkaTemplate bean for tests/mocking.
     *
     * This template overrides send(...) to log the attempt to stdout and return an
     * immediately completed SendResult without sending to Kafka.
     *
     * @return KafkaTemplate<String, String> that returns a completed SendResult for send calls.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        Map<String, Object> configProps = new HashMap<>();
        // Add Kafka producer configuration properties here
        
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configProps);
        
        return new KafkaTemplate<String, String>(producerFactory) {
            @Override
            @NonNull
            public ListenableFuture<SendResult<String, String>> send(@NonNull String topic, @Nullable String data) {
                return mockSend(topic, null, data);
            }

            @Override
            @NonNull
            public ListenableFuture<SendResult<String, String>> send(
                    @NonNull String topic,
                    @Nullable String key,
                    @Nullable String data
            ) {
                return mockSend(topic, key, data);
            }

            private ListenableFuture<SendResult<String, String>> mockSend(
                    @NonNull String topic,
                    @Nullable String key,
                    @Nullable String data
            ) {
                System.out.println("Mock Kafka: Sending data to topic " + topic + ": " + data);
                ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, key, data);
                RecordMetadata recordMetadata = new RecordMetadata(
                        new TopicPartition(topic, 0),
                        0L,
                        0L,
                        System.currentTimeMillis(),
                        0L,
                        key != null ? key.length() : 0,
                        data != null ? data.length() : 0
                );
                SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
                future.set(new SendResult<>(producerRecord, recordMetadata));
                return future;
            }
        };
    }
}
