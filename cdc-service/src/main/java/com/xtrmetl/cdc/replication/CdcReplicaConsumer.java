package com.xtrmetl.cdc.replication;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class CdcReplicaConsumer {

    private final ProcessedDataReplicaApplier processedDataReplicaApplier;

    public CdcReplicaConsumer(ProcessedDataReplicaApplier processedDataReplicaApplier) {
        this.processedDataReplicaApplier = processedDataReplicaApplier;
    }

    @KafkaListener(
            topicPattern = "${xtrmetl.replica.topic-pattern}",
            groupId = "${xtrmetl.replica.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        processedDataReplicaApplier.apply(record.topic(), record.key(), record.value());
    }
}

