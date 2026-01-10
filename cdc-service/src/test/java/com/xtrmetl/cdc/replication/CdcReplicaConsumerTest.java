package com.xtrmetl.cdc.replication;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CdcReplicaConsumerTest {

    @Mock
    private ProcessedDataReplicaApplier processedDataReplicaApplier;

    @Test
    void delegatesKafkaRecordToApplier() {
        CdcReplicaConsumer consumer = new CdcReplicaConsumer(processedDataReplicaApplier);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("xtrmetl-cdc.public.processed_data", 0, 0L, "k", "v");

        consumer.onMessage(record);

        verify(processedDataReplicaApplier).apply("xtrmetl-cdc.public.processed_data", "k", "v");
    }
}

