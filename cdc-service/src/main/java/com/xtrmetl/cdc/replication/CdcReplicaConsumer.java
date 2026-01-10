package com.xtrmetl.cdc.replication;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class CdcReplicaConsumer {

    private static final String SCHEMA_CHANGES_SUFFIX = ".schema-changes";

    private final ProcessedDataReplicaApplier processedDataReplicaApplier;
    private final SchemaChangeReplicaApplier schemaChangeReplicaApplier;

    public CdcReplicaConsumer(
            ProcessedDataReplicaApplier processedDataReplicaApplier,
            SchemaChangeReplicaApplier schemaChangeReplicaApplier
    ) {
        this.processedDataReplicaApplier = processedDataReplicaApplier;
        this.schemaChangeReplicaApplier = schemaChangeReplicaApplier;
    }

    @KafkaListener(
            topicPattern = "${xtrmetl.replica.topic-pattern}",
            groupId = "${xtrmetl.replica.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        if (topic != null && topic.endsWith(SCHEMA_CHANGES_SUFFIX)) {
            schemaChangeReplicaApplier.apply(topic, record.key(), record.value());
            return;
        }

        processedDataReplicaApplier.apply(topic, record.key(), record.value());
    }
}
