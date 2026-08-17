package com.yanfan.arena.platform.config;

import com.yanfan.arena.platform.match.error.MatchProcessingErrorClassifier;
import com.yanfan.arena.platform.match.error.MatchProcessingErrorType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.BackOff;

import java.util.List;

// Retry with the configured backoff,
// stop the listener when retries are exhausted.
public class RetryExhaustionErrorHandler extends DefaultErrorHandler {

    private final MatchProcessingErrorClassifier classifier;

    RetryExhaustionErrorHandler(
            ConsumerRecordRecoverer recoverer,
            BackOff backOff,
            MatchProcessingErrorClassifier classifier)
    {
        super(recoverer, backOff);
        this.classifier = classifier;
    }

    @Override
    public void handleRemaining(
            Exception thrownException,
            List<ConsumerRecord<?, ?>> records,
            Consumer<?, ?> consumer,
            MessageListenerContainer container)
    {
        boolean retryable = isRetryable(thrownException);

        // Retry, then publish the failed record to the DLT when retries run out
        super.handleRemaining(thrownException, records, consumer, container);

        // Spring recovered the record
        // For an exhausted retryable failure, stop abnormally and throw
        // So Spring cannot ack the source record's offset
        if (retryable) {
            container.stopAbnormally(
                    () -> {});

            throw new KafkaException("Retryable failure exhausted all attempts", thrownException);
        }

    }

    // Retryable means not permanent and not a deserialization failure
    private boolean isRetryable(Exception exception) {
        return !containsDeserializationException(exception)
                && classifier.classify(exception) == MatchProcessingErrorType.RETRYABLE;
    }

    // Verify if the failure is a deserialization failure
    private boolean containsDeserializationException(Throwable throwable) {

        while (throwable != null) {
            if (throwable instanceof DeserializationException) {
                return true;
            }

            throwable = throwable.getCause();
        }

        return false;
    }


}
