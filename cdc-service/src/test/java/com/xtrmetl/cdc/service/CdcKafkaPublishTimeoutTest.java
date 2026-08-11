package com.xtrmetl.cdc.service;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that a Kafka send future which never reaches a terminal state cannot stall CDC offset
 * handling indefinitely.
 */
class CdcKafkaPublishTimeoutTest {

    private static final long EXPECTED_ACKNOWLEDGEMENT_WAIT_MS = 65_000L;

    /**
     * Requires each application-level Kafka attempt to have a finite acknowledgement wait while
     * preserving fail-closed Debezium offset semantics.
     *
     * @throws Exception when mocked future or committer contracts surface checked failures
     */
    @Test
    void timesOutHungKafkaAcknowledgementsWithoutAdvancingOffsets() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate =
                (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> firstHungSend =
                (CompletableFuture<SendResult<String, String>>) mock(CompletableFuture.class);
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> secondHungSend =
                (CompletableFuture<SendResult<String, String>>) mock(CompletableFuture.class);

        when(firstHungSend.get(EXPECTED_ACKNOWLEDGEMENT_WAIT_MS, TimeUnit.MILLISECONDS))
                .thenThrow(new TimeoutException("first acknowledgement stalled"));
        when(secondHungSend.get(EXPECTED_ACKNOWLEDGEMENT_WAIT_MS, TimeUnit.MILLISECONDS))
                .thenThrow(new TimeoutException("second acknowledgement stalled"));
        when(kafkaTemplate.send("orders.customer_updates", "customer-42", "{\"op\":\"u\"}"))
                .thenReturn(firstHungSend, secondHungSend);

        CdcService cdcService = new CdcService(kafkaTemplate, false);
        ChangeEvent<String, String> event = event();
        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();

        KafkaException failure = assertThrows(
                KafkaException.class,
                () -> cdcService.handleChangeBatch(List.of(event), committer)
        );

        assertInstanceOf(TimeoutException.class, failure.getCause());
        verify(firstHungSend).get(EXPECTED_ACKNOWLEDGEMENT_WAIT_MS, TimeUnit.MILLISECONDS);
        verify(secondHungSend).get(EXPECTED_ACKNOWLEDGEMENT_WAIT_MS, TimeUnit.MILLISECONDS);
        verify(kafkaTemplate, times(2)).send(
                "orders.customer_updates", "customer-42", "{\"op\":\"u\"}"
        );
        verify(committer, never()).markProcessed(any());
        verify(committer, never()).markBatchFinished();
        assertEquals(0L, cdcService.getStatus().get("kafkaPublishSuccess"));
        assertEquals(2L, cdcService.getStatus().get("kafkaPublishFailure"));
    }

    @SuppressWarnings("unchecked")
    private static DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer() {
        return (DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>) mock(
                DebeziumEngine.RecordCommitter.class
        );
    }

    @SuppressWarnings("unchecked")
    private static ChangeEvent<String, String> event() {
        ChangeEvent<String, String> event = (ChangeEvent<String, String>) mock(ChangeEvent.class);
        when(event.destination()).thenReturn("orders.customer_updates");
        when(event.key()).thenReturn("customer-42");
        when(event.value()).thenReturn("{\"op\":\"u\"}");
        return event;
    }
}
