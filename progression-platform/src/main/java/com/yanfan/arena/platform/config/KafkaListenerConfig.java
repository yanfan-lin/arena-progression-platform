package com.yanfan.arena.platform.config;

import com.yanfan.arena.platform.error.ApiException;
import com.yanfan.arena.platform.match.error.MatchProcessingErrorClassifier;
import com.yanfan.arena.platform.match.error.MatchProcessingErrorType;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

@Configuration
public class KafkaListenerConfig {

    // Listener container factory for match events
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> dltKafkaTemplate,
            MatchProcessingErrorClassifier errorClassifier) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        configurer.configure(factory, consumerFactory);

        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);

        // Advance the offset only after the DLT write succeeds
        dltRecoverer.setFailIfSendResultIsError(true);

        // Retryable failures with 4 total attempts (initial, then 1s, 2s, and 4s)
        ExponentialBackOffWithMaxRetries retryBackOff = new ExponentialBackOffWithMaxRetries(3);

        retryBackOff.setInitialInterval(1000L);
        retryBackOff.setMultiplier(2.0);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) ->
                {
                    if (isDeserializationException(exception)) {
                        // Malformed JSON is published as raw bytes
                        dltRecoverer.accept(record, exception);
                    } else if (errorClassifier.classify(exception) == MatchProcessingErrorType.PERMANENT) {
                        // Permanent errors are published as ArenaMatchCompleted records
                        dltRecoverer.accept(record, exception);
                    } else {
                        // Retryable failures should not be sent to the DLT
                        throw new KafkaException("Retryable Kafka failures are not handled yet", exception);
                    }
                },
                retryBackOff
        );

        // Permanent failures go straight to the DLT without retrying
        errorHandler.addNotRetryableExceptions(ApiException.class);

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    private boolean isDeserializationException(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof DeserializationException) {
                return true;
            }

            throwable = throwable.getCause();
        }

        return false;
    }

}
