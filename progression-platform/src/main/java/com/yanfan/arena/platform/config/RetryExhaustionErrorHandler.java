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
import org.springframework.util.backoff.BackOff;

import java.util.List;

// Retry with the configured backoff,
// stop the listener when retries are exhausted.
public class RetryExhaustionErrorHandler extends DefaultErrorHandler {

    private final MatchProcessingErrorClassifier classifier;

    // Record whether DLT publishing failed during recovery
    private volatile boolean dltPublishFailed;

    RetryExhaustionErrorHandler(
            ConsumerRecordRecoverer recoverer,
            BackOff backOff,
            MatchProcessingErrorClassifier classifier)
    {
        super(recoverer, backOff);

        this.classifier = classifier;

        // Listen for recoverer failures so handleRemaining() can stop the listener
        setRetryListeners(new RetryListener() {
                              @Override
                              public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                                  // Only recoveryFailed is needed
                              }

                              @Override
                              public void recoveryFailed(ConsumerRecord<?, ?> record, Exception originalError,
                                                         Exception failureCause) {
                dltPublishFailed = true;
                              }
                          }
        );
    }

    @Override
    public void handleRemaining(
            Exception thrownException,
            List<ConsumerRecord<?, ?>> records,
            Consumer<?, ?> consumer,
            MessageListenerContainer container)
    {
        boolean retryable =
                classifier.classify(thrownException) == MatchProcessingErrorType.RETRYABLE;

        dltPublishFailed = false;

        try {
            // Retry, then publish the failed record to the DLT when retries run out
            super.handleRemaining(thrownException, records, consumer, container);
        }
        catch (RuntimeException ex) {
            // Only stop when the DLT write itself failed
            if (dltPublishFailed) {
                container.stopAbnormally(() -> {
                });

                throw new KafkaException("DLT publication failed", ex);
            }

            throw ex;
        }

        // For an exhausted retryable failure, stop abnormally and throw
        // So Spring cannot ack the source record's offset
        if (retryable) {
            container.stopAbnormally(
                    () -> {}
            );

            throw new KafkaException("Retryable failure exhausted all attempts", thrownException);
        }
    }

}
