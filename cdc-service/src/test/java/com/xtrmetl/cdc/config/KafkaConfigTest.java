package com.xtrmetl.cdc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
@ExtendWith(OutputCaptureExtension.class)
class KafkaConfigTest {

    @Test
    void configuresErrorHandlerWithDeadLetterRecovererAndBackOff() {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate, 1000L, 30L);

        Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
        assertNotNull(failureTracker);

        Object recoverer = ReflectionTestUtils.getField(failureTracker, "recoverer");
        assertNotNull(recoverer);
        assertTrue(recoverer instanceof DeadLetterPublishingRecoverer);

        Object backOff = ReflectionTestUtils.getField(failureTracker, "backOff");
        assertNotNull(backOff);
        assertTrue(backOff instanceof FixedBackOff);
        FixedBackOff fixedBackOff = (FixedBackOff) backOff;
        assertEquals(1000L, fixedBackOff.getInterval());
        assertEquals(30L, fixedBackOff.getMaxAttempts());

        BinaryExceptionClassifier classifier =
                (BinaryExceptionClassifier) ReflectionTestUtils.invokeMethod(errorHandler, "getClassifier");
        assertNotNull(classifier);
        assertFalse(classifier.classify(new IllegalArgumentException("test")));
        assertFalse(classifier.classify(new IllegalStateException("test")));
    }

    @Test
    void preservesZeroRetrySettings() {
        KafkaConfig config = new KafkaConfig();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate, 0L, 0L);

        Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
        assertNotNull(failureTracker);
        FixedBackOff fixedBackOff = (FixedBackOff) ReflectionTestUtils.getField(failureTracker, "backOff");
        assertNotNull(fixedBackOff);
        assertEquals(0L, fixedBackOff.getInterval());
        assertEquals(0L, fixedBackOff.getMaxAttempts());
    }

    @Test
    void rejectsNegativeRetryBackoffBeforeBuildingErrorHandler() {
        KafkaConfig config = new KafkaConfig();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.kafkaListenerErrorHandler(kafkaTemplate, -1L, 30L)
        );

        assertTrue(exception.getMessage().contains("xtrmetl.replica.kafka.retry-backoff-ms"));
    }

    @Test
    void rejectsNegativeRetryAttemptsBeforeBuildingErrorHandler() {
        KafkaConfig config = new KafkaConfig();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.kafkaListenerErrorHandler(kafkaTemplate, 1000L, -1L)
        );

        assertTrue(exception.getMessage().contains("xtrmetl.replica.kafka.retry-max-attempts"));
    }

    @Test
    void configuresListenerFactoryWithRecordAckModeAndCommonErrorHandler() {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate, 1000L, 30L);
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler, 1);

        assertEquals(ContainerProperties.AckMode.RECORD, factory.getContainerProperties().getAckMode());
        assertSame(errorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
    }

    @Test
    void defaultsConcurrencyToOneWhenInvalid(CapturedOutput output) {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate, 1000L, 30L);
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler, 0);

        assertEquals(1, ReflectionTestUtils.getField(factory, "concurrency"));
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("Invalid xtrmetl.replica.kafka.concurrency=0"));
    }

    @Test
    void capsConcurrencyWhenAboveMax(CapturedOutput output) {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate, 1000L, 30L);
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler, 100);

        assertEquals(32, ReflectionTestUtils.getField(factory, "concurrency"));
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("capping to 32"));
    }

    @Test
    void warnsWhenConcurrencyGreaterThanOne(CapturedOutput output) {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate, 1000L, 30L);
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler, 2);

        assertEquals(2, ReflectionTestUtils.getField(factory, "concurrency"));
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("out-of-order processing risk"));
    }
}
