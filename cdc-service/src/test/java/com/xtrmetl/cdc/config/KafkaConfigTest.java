package com.xtrmetl.cdc.config;

import org.junit.jupiter.api.Test;
import org.springframework.classify.BinaryExceptionClassifier;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SuppressWarnings("null")
class KafkaConfigTest {

    @Test
    void configuresErrorHandlerWithDeadLetterRecovererAndBackOff() {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate);

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
        assertEquals(3L, fixedBackOff.getMaxAttempts());

        BinaryExceptionClassifier classifier =
                (BinaryExceptionClassifier) ReflectionTestUtils.invokeMethod(errorHandler, "getClassifier");
        assertNotNull(classifier);
        assertFalse(classifier.classify(new IllegalArgumentException("test")));
    }

    @Test
    void configuresListenerFactoryWithRecordAckModeAndCommonErrorHandler() {
        KafkaConfig config = new KafkaConfig();

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.kafkaListenerErrorHandler(kafkaTemplate);
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertEquals(ContainerProperties.AckMode.RECORD, factory.getContainerProperties().getAckMode());
        assertSame(errorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
    }
}
