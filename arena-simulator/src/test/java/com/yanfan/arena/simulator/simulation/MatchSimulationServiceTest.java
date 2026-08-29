package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.messaging.MatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// Verify a simulated match is returned only after Kafka acknowledgement.
@ExtendWith(MockitoExtension.class)
class MatchSimulationServiceTest {

    @Mock
    private MatchGenerator matchGenerator;

    @Mock
    private MatchEventPublisher matchEventPublisher;

    @Mock
    private ArenaMatchCompleted event;

    @Mock
    private SendResult<String, ArenaMatchCompleted> sendResult;

    private MatchSimulationService matchSimulationService;

    @BeforeEach
    void setUp() {
        matchSimulationService =
                new MatchSimulationService(matchGenerator, matchEventPublisher);
    }

    @Test
    void waitsForKafkaAckBeforeReturningMatch() {

        CompletableFuture<SendResult<String, ArenaMatchCompleted>>
                ack = new CompletableFuture<>();

        when(matchGenerator.generateMatch(MatchMode.THREE_VS_THREE))
                .thenReturn(event);

        when(matchEventPublisher.publish(event))
                .thenReturn(ack);

        // Run simulateMatch() separately because it waits for Kafka ACK
        CompletableFuture<ArenaMatchCompleted> simulation =
                CompletableFuture.supplyAsync(() -> matchSimulationService.simulateMatch(MatchMode.THREE_VS_THREE));

        // Wait until the match simulation reaches the publisher
        verify(matchEventPublisher, timeout(1000))
                .publish(event);

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
