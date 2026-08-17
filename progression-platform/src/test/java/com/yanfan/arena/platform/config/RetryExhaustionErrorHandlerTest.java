package com.yanfan.arena.platform.config;

import com.yanfan.arena.platform.match.error.MatchProcessingErrorClassifier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// Verify that the handler stops the listener when the DLT write itself fails.
class RetryExhaustionErrorHandlerTest {

    // Simulate a DLT producer that fails to send, then confirm the handler stops abnormally
    @Test
    void stopsListenerWhenDltPublicationFails() {

        MatchProcessingErrorClassifier classifier = new MatchProcessingErrorClassifier();

        ConsumerRecordRecoverer failingRecoverer = (record, exception) -> {
            throw new KafkaException("DLT send failed");
        };

        RetryExhaustionErrorHandler handler = new RetryExhaustionErrorHandler(
                failingRecoverer,
                new FixedBackOff(0L, 0L),
                classifier
        );

        MessageListenerContainer container = mock(MessageListenerContainer.class);
        Consumer<Object, Object> consumer = mock(Consumer.class);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "arena-match-completed",
                        0,
                        0L,
                        "key",
                        "value");

        assertThatThrownBy(() -> handler.handleRemaining(
                new IllegalStateException("retryable"),
                List.of(record),
                consumer,
                container)
        )
                .isInstanceOf(KafkaException.class);

        verify(container).stopAbnormally(any(Runnable.class));
    }


}
