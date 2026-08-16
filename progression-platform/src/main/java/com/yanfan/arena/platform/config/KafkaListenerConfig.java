package com.yanfan.arena.platform.config;

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
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

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

        // Malformed JSON is published as raw bytes because it cannot be deserialized
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) ->
                {
                    if (isDeserializationException(exception)) {
                        dltRecoverer.accept(record, exception);
                    } else if (errorClassifier.classify(exception) == MatchProcessingErrorType.PERMANENT) {
                        // Permanent errors are published as typed ArenaMatchCompleted records
                        dltRecoverer.accept(record, exception);
                    } else {
                        // Retryable failures should not be sent to the DLT
                        throw new KafkaException("Retryable Kafka failures are not handled yet", exception);
                    }
                },
                new FixedBackOff(0L, 0L)
        );

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
