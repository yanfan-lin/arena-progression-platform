package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.match.processing.MatchProcessingResult;
import com.yanfan.arena.platform.match.processing.MatchProcessor;
import com.yanfan.arena.platform.match.validation.MatchEventValidationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Verify that listener hands a valid event to MatchProcessor.
@ExtendWith(MockitoExtension.class)
class MatchEventListenerTest {

    @Mock
    MatchProcessor matchProcessor;

    @InjectMocks
    MatchEventListener listener;

    @Test
    void validEventIsDelegatedToMatchProcessor() {
        ArenaMatchCompleted event = validEvent();

        when(matchProcessor.process(any(ArenaMatchCompleted.class)))
                .thenReturn(new MatchProcessingResult(
                        MatchProcessingResult.MatchProcessingOutcome.PROCESSED,
                        null,
                        null));

        listener.onMatchEvent(new ConsumerRecord<>(
                "arena-match-completed",
                0,
                0L,
                event.matchId().toString(),
                event));

        verify(matchProcessor).process(event);
    }

    @Test
    void mismatchedKeyIsRejectedBeforeProcessing() {
        ArenaMatchCompleted event = validEvent();

        assertThrows(MatchEventValidationException.class,
                () -> listener.onMatchEvent(new ConsumerRecord<>(
                        "arena-match-completed",
                        0,
                        0L,
                        "wrong-key",
                        event)));

        verifyNoInteractions(matchProcessor);
    }

    @Test
    void nullKeyIsRejectedBeforeProcessing() {
        ArenaMatchCompleted event = validEvent();

        assertThrows(MatchEventValidationException.class,
                () -> listener.onMatchEvent(new ConsumerRecord<>(
                        "arena-match-completed",
                        0,
                        0L,
                        null,
                        event)));

        verifyNoInteractions(matchProcessor);
    }

    // Build a valid 3v3 event where team 1 wins
    private ArenaMatchCompleted validEvent() {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                MatchMode.THREE_VS_THREE,
                Instant.now(),
                1L,
                List.of(
                        new ArenaMatchCompleted.Team(1L, List.of(
                                new ArenaMatchCompleted.Player(101L, 5, 2, 3),
                                new ArenaMatchCompleted.Player(102L, 2, 1, 1),
                                new ArenaMatchCompleted.Player(103L, 0, 0, 0))),
                        new ArenaMatchCompleted.Team(2L, List.of(
                                new ArenaMatchCompleted.Player(201L, 1, 4, 2),
                                new ArenaMatchCompleted.Player(202L, 0, 1, 1),
                                new ArenaMatchCompleted.Player(203L, 2, 2, 0)))));
    }

}
