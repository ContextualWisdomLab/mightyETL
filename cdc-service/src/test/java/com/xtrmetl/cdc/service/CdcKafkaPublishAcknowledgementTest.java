package com.xtrmetl.cdc.service;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the PostgreSQL-to-Kafka delivery boundary so Debezium offsets are not marked processed
 * before Kafka has acknowledged the corresponding change event.
 */
class CdcKafkaPublishAcknowledgementTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private CdcService cdcService;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> template = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        kafkaTemplate = template;
        cdcService = new CdcService(kafkaTemplate, false);
    }

    @Test
    void waitsForBrokerAcknowledgementBeforeMarkingDebeziumOffset() throws Exception {
        ChangeEvent<String, String> event = event("orders.customer_updates", "customer-42", "{\"op\":\"u\"}");
        CompletableFuture<SendResult<String, String>> pendingAcknowledgement = new CompletableFuture<>();
        when(kafkaTemplate.send("orders.customer_updates", "customer-42", "{\"op\":\"u\"}"))
                .thenReturn(pendingAcknowledgement);

        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> processing = executor.submit(() -> {
                try {
                    cdcService.handleChangeBatch(List.of(event), committer);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruptedException);
                }
            });

            verify(kafkaTemplate, timeout(1_000)).send(
                    "orders.customer_updates", "customer-42", "{\"op\":\"u\"}"
            );
            assertThrows(TimeoutException.class, () -> processing.get(100, TimeUnit.MILLISECONDS));
            verify(committer, never()).markProcessed(any());
            verify(committer, never()).markBatchFinished();

            pendingAcknowledgement.complete(mock(SendResult.class));
            processing.get(1, TimeUnit.SECONDS);

            var ordered = inOrder(committer);
            ordered.verify(committer).markProcessed(event);
            ordered.verify(committer).markBatchFinished();
            assertEquals(1L, cdcService.getStatus().get("kafkaPublishSuccess"));
            assertEquals(0L, cdcService.getStatus().get("kafkaPublishFailure"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retriesFailedKafkaPublicationBeforeOffsetCommit() throws Exception {
        ChangeEvent<String, String> event = event("orders.customer_updates", "customer-42", "{\"op\":\"u\"}");
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new KafkaException("broker unavailable"));
        CompletableFuture<SendResult<String, String>> succeeded =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send("orders.customer_updates", "customer-42", "{\"op\":\"u\"}"))
                .thenReturn(failed, succeeded);

        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();
        cdcService.handleChangeBatch(List.of(event), committer);

        verify(kafkaTemplate, times(2)).send(
                "orders.customer_updates", "customer-42", "{\"op\":\"u\"}"
        );
        verify(committer).markProcessed(event);
        verify(committer).markBatchFinished();
        assertEquals(1L, cdcService.getStatus().get("kafkaPublishSuccess"));
        assertEquals(1L, cdcService.getStatus().get("kafkaPublishFailure"));
    }

    @Test
    void exhaustsBoundedKafkaPublicationAttemptsWithoutOffsetCommit() {
        ChangeEvent<String, String> event = event("orders.customer_updates", "customer-42", "{\"op\":\"u\"}");
        CompletableFuture<SendResult<String, String>> firstFailure = new CompletableFuture<>();
        firstFailure.completeExceptionally(new KafkaException("broker unavailable"));
        CompletableFuture<SendResult<String, String>> secondFailure = new CompletableFuture<>();
        secondFailure.completeExceptionally(new KafkaException("broker still unavailable"));
        when(kafkaTemplate.send("orders.customer_updates", "customer-42", "{\"op\":\"u\"}"))
                .thenReturn(firstFailure, secondFailure);

        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();

        KafkaException failure = assertThrows(
                KafkaException.class,
                () -> cdcService.handleChangeBatch(List.of(event), committer)
        );

        assertTrue(failure.getMessage().contains("2 attempts"));
        verify(kafkaTemplate, times(2)).send(
                "orders.customer_updates", "customer-42", "{\"op\":\"u\"}"
        );
        verify(committer, never()).markProcessed(any());
        verify(committer, never()).markBatchFinished();
        assertEquals(0L, cdcService.getStatus().get("kafkaPublishSuccess"));
        assertEquals(2L, cdcService.getStatus().get("kafkaPublishFailure"));
    }

    @Test
    void retriesSynchronousKafkaSendFailureBeforeOffsetCommit() throws Exception {
        ChangeEvent<String, String> event = event("orders.customer_updates", "customer-42", "{\"op\":\"u\"}");
        CompletableFuture<SendResult<String, String>> succeeded =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send("orders.customer_updates", "customer-42", "{\"op\":\"u\"}"))
                .thenThrow(new KafkaException("producer temporarily unavailable"))
                .thenReturn(succeeded);

        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();
        cdcService.handleChangeBatch(List.of(event), committer);

        verify(kafkaTemplate, times(2)).send(
                "orders.customer_updates", "customer-42", "{\"op\":\"u\"}"
        );
        verify(committer).markProcessed(event);
        verify(committer).markBatchFinished();
        assertEquals(1L, cdcService.getStatus().get("kafkaPublishSuccess"));
        assertEquals(1L, cdcService.getStatus().get("kafkaPublishFailure"));
    }

    @Test
    void keylessEventIsAcknowledgedBeforeOffsetCommit() throws Exception {
        ChangeEvent<String, String> event = event("orders.customer_updates", null, "{\"op\":\"d\"}");
        when(kafkaTemplate.send("orders.customer_updates", "{\"op\":\"d\"}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();
        cdcService.handleChangeBatch(List.of(event), committer);

        verify(kafkaTemplate).send("orders.customer_updates", "{\"op\":\"d\"}");
        verify(committer).markProcessed(event);
        verify(committer).markBatchFinished();
    }

    @Test
    void eventWithoutDestinationAdvancesWithoutPublishing() throws Exception {
        ChangeEvent<String, String> event = event(null, "customer-42", "{\"op\":\"u\"}");
        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();

        cdcService.handleChangeBatch(List.of(event), committer);

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class));
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(String.class));
        verify(committer).markProcessed(event);
        verify(committer).markBatchFinished();
        assertEquals(0L, cdcService.getStatus().get("kafkaPublishSuccess"));
        assertEquals(0L, cdcService.getStatus().get("kafkaPublishFailure"));
    }

    @Test
    void interruptedAcknowledgementStopsBatchWithoutMarkingOffset() throws Exception {
        ChangeEvent<String, String> event = event("orders.customer_updates", "customer-42", "{\"op\":\"u\"}");
        CompletableFuture<SendResult<String, String>> pendingAcknowledgement = new CompletableFuture<>();
        when(kafkaTemplate.send("orders.customer_updates", "customer-42", "{\"op\":\"u\"}"))
                .thenReturn(pendingAcknowledgement);
        DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer = committer();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread processing = new Thread(() -> {
            try {
                cdcService.handleChangeBatch(List.of(event), committer);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "cdc-kafka-ack-test");
        processing.start();
        verify(kafkaTemplate, timeout(1_000)).send(
                "orders.customer_updates", "customer-42", "{\"op\":\"u\"}"
        );

        processing.interrupt();
        processing.join(1_000);

        assertFalse(processing.isAlive(), "interrupted CDC publication must stop promptly");
        assertInstanceOf(InterruptedException.class, failure.get());
        verify(committer, never()).markProcessed(any());
        verify(committer, never()).markBatchFinished();
    }

    @Test
    void producerConfigurationRequiresDurableAcknowledgedDelivery() throws Exception {
        String configuration = Files.readString(
                projectRoot().resolve("cdc-service/src/main/resources/application.yml"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(configuration.contains("acks: all"));
        assertTrue(configuration.contains("\"[enable.idempotence]\": true"));
        assertTrue(configuration.contains(
                "\"[delivery.timeout.ms]\": ${CDC_KAFKA_DELIVERY_TIMEOUT_MS:60000}"
        ));
        assertTrue(configuration.contains(
                "\"[max.block.ms]\": ${CDC_KAFKA_MAX_BLOCK_MS:30000}"
        ));
    }

    @SuppressWarnings("unchecked")
    private static DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer() {
        return (DebeziumEngine.RecordCommitter<ChangeEvent<String, String>>) mock(
                DebeziumEngine.RecordCommitter.class
        );
    }

    @SuppressWarnings("unchecked")
    private static ChangeEvent<String, String> event(String destination, String key, String value) {
        ChangeEvent<String, String> event = (ChangeEvent<String, String>) mock(ChangeEvent.class);
        when(event.destination()).thenReturn(destination);
        when(event.key()).thenReturn(key);
        when(event.value()).thenReturn(value);
        return event;
    }

    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPom = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPom = current;
            }
            current = current.getParent();
        }
        if (lastPom != null) {
            return lastPom;
        }
        throw new IllegalStateException("project root not found");
    }
}
