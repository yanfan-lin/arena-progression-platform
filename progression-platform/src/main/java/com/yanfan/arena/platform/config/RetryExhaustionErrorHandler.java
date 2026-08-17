package com.yanfan.arena.platform.config;

import com.yanfan.arena.platform.match.error.MatchProcessingErrorClassifier;
import com.yanfan.arena.platform.match.error.MatchProcessingErrorType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.BackOff;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Retry with the configured backoff,
// stop the listener when retries are exhausted.
public class RetryExhaustionErrorHandler extends DefaultErrorHandler {

    private final MatchProcessingErrorClassifier classifier;

    // Set when the DLT recoverer throws
    // To separate a failed DLT write from a normal retry
    private final AtomicBoolean dltPublishFailed = new AtomicBoolean();

    RetryExhaustionErrorHandler(
            ConsumerRecordRecoverer recoverer,
            BackOff backOff,
            MatchProcessingErrorClassifier classifier)
    {
        super(recoverer, backOff);

        this.classifier = classifier;

        // Listen for recoverer failures
        // so handleRemaining can stop the listener
        setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                // Only recoveryFailed is needed
            }

            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception originalError,
                                       Exception failureCause)
            {
                dltPublishFailed.set(true);
            }
        });
    }

    @Override
    public void handleRemaining(
            Exception thrownException,
            List<ConsumerRecord<?, ?>> records,
            Consumer<?, ?> consumer,
            MessageListenerContainer container)
    {
        boolean retryable = isRetryable(thrownException);

        dltPublishFailed.set(false);

        try {
            // Retry, then publish the failed record to the DLT when retries run out
            super.handleRemaining(thrownException, records, consumer, container);
        }
        catch (RuntimeException ex) {
            // Only stop when the DLT write itself failed
            if (dltPublishFailed.get()) {
                container.stopAbnormally(() -> { });

                throw new KafkaException("DLT publication failed", ex);
            }

            throw ex;
        }

        // For an exhausted retryable failure, stop abnormally and throw
        // So Spring cannot ack the source record's offset
        if (retryable) {
            container.stopAbnormally(
                    () -> { }
            );

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
