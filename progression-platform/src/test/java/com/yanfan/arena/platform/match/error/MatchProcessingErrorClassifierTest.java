package com.yanfan.arena.platform.match.error;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.validation.MatchEventValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

// Validate that API errors are permanent, while database connection,
// lock, and unexpected errors are retryable.
class MatchProcessingErrorClassifierTest {

    private final MatchProcessingErrorClassifier classifier = new MatchProcessingErrorClassifier();

    @Test
    void validationErrorsArePermanent() {
        assertThat(classifier.classify(new MatchEventValidationException("invalid")))
                .isEqualTo(MatchProcessingErrorType.PERMANENT);
    }

    @Test
    void businessErrorsArePermanent() {
        assertThat(classifier.classify(new ResourceNotFoundException("TEAM_NOT_FOUND", "not found")))
                .isEqualTo(MatchProcessingErrorType.PERMANENT);
        assertThat(classifier.classify(new ConflictException("TEAM_NOT_DRAFT", "conflict")))
                .isEqualTo(MatchProcessingErrorType.PERMANENT);
    }

    @Test
    void databaseConnectionErrorsAreRetryable() {
        assertThat(classifier.classify(new DataAccessResourceFailureException("db down")))
                .isEqualTo(MatchProcessingErrorType.RETRYABLE);
    }

    @Test
    void lockErrorsAreRetryable() {
        assertThat(classifier.classify(new PessimisticLockingFailureException("locked")))
                .isEqualTo(MatchProcessingErrorType.RETRYABLE);
    }

    @Test
    void unknownErrorsAreRetryable() {
        assertThat(classifier.classify(new RuntimeException("unexpected")))
                .isEqualTo(MatchProcessingErrorType.RETRYABLE);
    }

}
