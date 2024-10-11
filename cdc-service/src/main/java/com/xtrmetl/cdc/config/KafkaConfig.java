package com.xtrmetl.cdc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<String, String>(null) {
            @Override
            public void send(String topic, String data) {
                System.out.println("Mock Kafka: Sending data to topic " + topic + ": " + data);
            }
        };
    }
}
