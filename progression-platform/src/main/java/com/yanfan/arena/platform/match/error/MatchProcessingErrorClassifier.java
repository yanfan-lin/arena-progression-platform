package com.yanfan.arena.platform.match.error;

import com.yanfan.arena.platform.error.ApiException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

// Classify match-processing failures for Kafka retry and DLT handling.
@Component
public class MatchProcessingErrorClassifier {

    // Treat API errors as permanent because retrying the same event would not fix them.
    // Treat unexpected failures as retryable.
    public MatchProcessingErrorType classify(Throwable error) {

        Throwable current = error;

        while (current != null) {
            if (current instanceof DeserializationException) {
                return MatchProcessingErrorType.DESERIALIZATION;
            }

            if (current instanceof ApiException) {
                return MatchProcessingErrorType.PERMANENT;
            }

            current = current.getCause();
        }

        return MatchProcessingErrorType.RETRYABLE;
    }

}
