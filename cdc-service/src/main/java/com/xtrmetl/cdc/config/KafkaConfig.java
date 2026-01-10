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
     * 테스트 및 모킹 목적의 KafkaTemplate 빈을 생성한다.
     *
     * 이 KafkaTemplate은 내부적으로 send(topic, data)를 오버라이드하여 실제 메시지 전송을 수행하지 않고
     * 표준 출력에 전송 시도 내용을 기록한 뒤 즉시 성공한 SendResult를 담은 ListenableFuture를 반환한다.
     *
     * @return KafkaTemplate<String, String> 인스턴스. send 호출이 실제 전송 대신 로그 출력과 즉시 완료된 SendResult를 반환하도록 오버라이드되어 있음.
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
                System.out.println("Mock Kafka: Sending data to topic " + topic + ": " + data);
                ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, data);
                RecordMetadata recordMetadata = new RecordMetadata(
                        new TopicPartition(topic, 0),
                        0L,
                        0L,
                        System.currentTimeMillis(),
                        Long.valueOf(0L),
                        0,
                        data != null ? data.length() : 0
                );
                SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
                future.set(new SendResult<>(producerRecord, recordMetadata));
                return future;
            }
        };
    }
}
