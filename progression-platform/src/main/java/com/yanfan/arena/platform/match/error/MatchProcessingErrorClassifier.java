package com.yanfan.arena.platform.match.error;

import com.yanfan.arena.platform.error.ApiException;
import org.springframework.stereotype.Component;

// Decide whether a match-processing failure is permanent or retryable.
@Component
public class MatchProcessingErrorClassifier {

    // Treat API errors as permanent because retrying the same event would not fix them.
    // Treat unexpected failures as retryable.
    public MatchProcessingErrorType classify(Throwable error) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof ApiException) {
                return MatchProcessingErrorType.PERMANENT;
            }

            current = current.getCause();
        }

        return MatchProcessingErrorType.RETRYABLE;
    }

}
