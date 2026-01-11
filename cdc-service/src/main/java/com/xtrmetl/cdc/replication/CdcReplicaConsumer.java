package com.xtrmetl.cdc.replication;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class CdcReplicaConsumer {

    private final ProcessedDataReplicaApplier processedDataReplicaApplier;
    private final SchemaChangeReplicaApplier schemaChangeReplicaApplier;

    /**
     * CDC 복제를 처리하는 소비자 컴포넌트를 생성하고 필요한 복제 적용기(applier)를 주입한다.
     *
     * @param processedDataReplicaApplier 처리된 데이터 복제 이벤트를 적용하는 객체
     * @param schemaChangeReplicaApplier 스키마 변경 복제 이벤트를 적용하는 객체
     */
    public CdcReplicaConsumer(
            ProcessedDataReplicaApplier processedDataReplicaApplier,
            SchemaChangeReplicaApplier schemaChangeReplicaApplier
    ) {
        this.processedDataReplicaApplier = processedDataReplicaApplier;
        this.schemaChangeReplicaApplier = schemaChangeReplicaApplier;
    }

    /**
     * 토픽 접미사에 따라 CDC 복제 메시지를 적절한 레플리카 applier로 라우팅하여 처리한다.
     *
     * <p>레코드의 토픽이 ".schema-changes"로 끝나면 스키마 변경 처리기로 전달하고, 그렇지 않으면 처리된 데이터 복제기로 전달한다.</p>
     *
     * @param record Kafka로부터 수신된 레코드(토픽, 키, 값)
     */
    @KafkaListener(
            topicPattern = "${xtrmetl.replica.topic-pattern}",
            groupId = "${xtrmetl.replica.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        if (topic != null && topic.endsWith(ReplicaTopics.SCHEMA_CHANGES_SUFFIX)) {
            schemaChangeReplicaApplier.apply(topic, record.key(), record.value());
            return;
        }

        processedDataReplicaApplier.apply(topic, record.key(), record.value());
    }
}
