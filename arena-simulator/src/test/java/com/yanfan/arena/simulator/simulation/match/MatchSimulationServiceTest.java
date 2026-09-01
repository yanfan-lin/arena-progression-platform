package com.yanfan.arena.simulator.simulation.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import com.yanfan.arena.contract.MatchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// Verify a simulated match is returned only after Kafka acknowledgement.
@ExtendWith(MockitoExtension.class)
class MatchSimulationServiceTest {

    @Mock
    private MatchGenerator matchGenerator;

    @Mock
    private KafkaTemplate<String, ArenaMatchCompleted> kafkaTemplate;

    @Mock
    private ArenaMatchCompleted event;

    @Mock
    private SendResult<String, ArenaMatchCompleted> sendResult;

    private MatchSimulationService matchSimulationService;

    @BeforeEach
    void setUp() {
        matchSimulationService =
                new MatchSimulationService(matchGenerator, kafkaTemplate);
    }

    @Test
    void waitsForKafkaAckBeforeReturningMatch() {

        CompletableFuture<SendResult<String, ArenaMatchCompleted>>
                ack = new CompletableFuture<>();

        UUID matchId = UUID.randomUUID();

        when(matchGenerator.generateMatch(MatchMode.THREE_VS_THREE))
                .thenReturn(event);

        when(event.matchId())
                .thenReturn(matchId);

        when(kafkaTemplate.send(
                KafkaTopics.MATCH_COMPLETED,
                matchId.toString(),
                event))
                .thenReturn(ack);

        // Run simulateMatch() separately because it waits for Kafka ACK
        CompletableFuture<ArenaMatchCompleted> simulation =
                CompletableFuture.supplyAsync(() -> matchSimulationService.simulateMatch(MatchMode.THREE_VS_THREE));

        // Wait until the match simulation reaches the publisher
        verify(kafkaTemplate, timeout(1000))
                .send(
                        KafkaTopics.MATCH_COMPLETED,
                        matchId.toString(),
                        event);

        // simulateMatch() should not be returned before Kafka acknowledges the event
        assertThat(simulation.isDone())
                .isFalse();

        // Kafka acks the published event
        ack.complete(sendResult);

        // simulateMatch() should now return the same generated event
        assertThat(simulation.join())
                .isSameAs(event);
    }

}
