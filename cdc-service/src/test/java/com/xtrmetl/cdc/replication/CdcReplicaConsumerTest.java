package com.xtrmetl.cdc.replication;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CdcReplicaConsumerTest {

    @Mock
    private ProcessedDataReplicaApplier processedDataReplicaApplier;

    @Mock
    private SchemaChangeReplicaApplier schemaChangeReplicaApplier;

    @Test
    void delegatesDataTopicToProcessedDataApplier() {
        CdcReplicaConsumer consumer = new CdcReplicaConsumer(processedDataReplicaApplier, schemaChangeReplicaApplier);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("xtrmetl-cdc.public.processed_data", 0, 0L, "k", "v");

        consumer.onMessage(record);

        verify(processedDataReplicaApplier).apply("xtrmetl-cdc.public.processed_data", "k", "v");
        verify(schemaChangeReplicaApplier, never()).apply("xtrmetl-cdc.public.processed_data", "k", "v");
    }

    @Test
    void delegatesSchemaChangesTopicToSchemaChangeApplier() {
        CdcReplicaConsumer consumer = new CdcReplicaConsumer(processedDataReplicaApplier, schemaChangeReplicaApplier);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("xtrmetl-cdc.schema-changes", 0, 0L, "k", "v");

        consumer.onMessage(record);

        verify(schemaChangeReplicaApplier).apply("xtrmetl-cdc.schema-changes", "k", "v");
        verify(processedDataReplicaApplier, never()).apply("xtrmetl-cdc.schema-changes", "k", "v");
    }

    @Test
    void delegatesNullTopicToProcessedDataApplier() {
        CdcReplicaConsumer consumer = new CdcReplicaConsumer(processedDataReplicaApplier, schemaChangeReplicaApplier);
        @SuppressWarnings("unchecked")
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);
        when(record.topic()).thenReturn(null);
        when(record.key()).thenReturn("k");
        when(record.value()).thenReturn("v");

        consumer.onMessage(record);

        verify(processedDataReplicaApplier).apply(null, "k", "v");
        verify(schemaChangeReplicaApplier, never()).apply(null, "k", "v");
    }
}
