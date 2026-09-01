package com.yanfan.arena.platform.config;

import com.yanfan.arena.platform.error.ApiException;
import com.yanfan.arena.platform.match.error.MatchProcessingErrorClassifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Configuration
public class KafkaListenerConfig {

    // Listener container factory for match events
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> dltKafkaTemplate,
            MatchProcessingErrorClassifier errorClassifier)
    {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        configurer.configure(factory, consumerFactory);

        // Track the delivery attempt so the DLT can report it
        factory.getContainerProperties().setDeliveryAttemptHeader(true);

        DeadLetterPublishingRecoverer dltRecoverer =
                new DeadLetterPublishingRecoverer(dltKafkaTemplate);

        // Advance the offset only after the DLT write succeeds
        dltRecoverer.setFailIfSendResultIsError(true);

        // Add diagnostic headers for dead-letter records
        dltRecoverer.addHeadersFunction((record, exception) -> {

            Headers headers = new RecordHeaders();

            headers.add(new RecordHeader(
                    "failure-category",
                    errorClassifier.classify(exception).name()
                            .getBytes(StandardCharsets.UTF_8))
            );

            headers.add(new RecordHeader(
                    "attempt",
                    String.valueOf(attemptCount(record))
                            .getBytes(StandardCharsets.UTF_8))
            );

            return headers;
        });

        // Retryable failures with 4 total attempts (initial, then 1s, 2s, and 4s)
        ExponentialBackOffWithMaxRetries retryBackOff = new ExponentialBackOffWithMaxRetries(3);

        retryBackOff.setInitialInterval(1000L);
        retryBackOff.setMultiplier(2.0);

        // Recover failed records to the DLT,
        // and stop the listener when retries are exhausted
        RetryExhaustionErrorHandler errorHandler = new RetryExhaustionErrorHandler(
                dltRecoverer,
                retryBackOff,
                errorClassifier);

        // Permanent failures go straight to the DLT without retrying
        errorHandler.addNotRetryableExceptions(ApiException.class);

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // Read the delivery-attempt header, or default to one when absent
    private int attemptCount(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        if (header == null) {
            return 1;
        }

        return ByteBuffer.wrap(header.value()).getInt();
    }

}
