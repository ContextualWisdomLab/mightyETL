package com.xtrmetl.cdc.replication;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Routes live CDC replica records to the matching replica applier.
 *
 * <p>Dead-letter topics are terminal quarantine inputs for operator recovery and are deliberately
 * ignored by this live replica path, even when a configured topic pattern also matches them.</p>
 */
@Component
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class CdcReplicaConsumer {

    private final ProcessedDataReplicaApplier processedDataReplicaApplier;
    private final SchemaChangeReplicaApplier schemaChangeReplicaApplier;

    /**
     * Creates the CDC replica consumer with the appliers used for live data and schema events.
     *
     * @param processedDataReplicaApplier applies supported data-row events to the replica
     * @param schemaChangeReplicaApplier applies permitted schema-change events to the replica
     */
    public CdcReplicaConsumer(
            ProcessedDataReplicaApplier processedDataReplicaApplier,
            SchemaChangeReplicaApplier schemaChangeReplicaApplier
    ) {
        this.processedDataReplicaApplier = processedDataReplicaApplier;
        this.schemaChangeReplicaApplier = schemaChangeReplicaApplier;
    }

    /**
     * Routes one live CDC record without feeding terminal dead-letter records back into appliers.
     *
     * <p>A topic ending in {@code .DLT} is a terminal dead-letter record and is acknowledged by the
     * listener without invoking either replica applier. Schema-change topics are routed to the
     * schema applier; every other live topic keeps the existing data-applier behavior.</p>
     *
     * @param record Kafka record containing the source topic, key, and Debezium-compatible value
     */
    @KafkaListener(
            topicPattern = "${xtrmetl.replica.topic-pattern}",
            groupId = "${xtrmetl.replica.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        if (topic != null && topic.endsWith(ReplicaTopics.DEAD_LETTER_SUFFIX)) {
            return;
        }
        if (topic != null && topic.endsWith(ReplicaTopics.SCHEMA_CHANGES_SUFFIX)) {
            schemaChangeReplicaApplier.apply(topic, record.key(), record.value());
            return;
        }

        processedDataReplicaApplier.apply(topic, record.key(), record.value());
    }
}
