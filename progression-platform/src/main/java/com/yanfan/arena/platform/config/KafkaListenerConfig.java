package com.yanfan.arena.platform.config;

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
            KafkaTemplate<String, Object> dltKafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        configurer.configure(factory, consumerFactory);

        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);

        // Advance the offset only after the DLT write succeeds
        dltRecoverer.setFailIfSendResultIsError(true);

        // Route only malformed records to the DLT.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    if (isDeserializationException(exception)) {
                        dltRecoverer.accept(record, exception);
                    } else {
                        throw new KafkaException("Only malformed Kafka records are published to the dead-letter topic", exception);
                    }
                },
                new FixedBackOff(0L, 0L));

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
