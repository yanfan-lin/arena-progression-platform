package com.yanfan.arena.simulator.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

// Publish generated match events to the platform through Kafka.
@Component
public class MatchEventPublisher {

    private final KafkaTemplate<String, ArenaMatchCompleted> kafkaTemplate;

    @Autowired
    public MatchEventPublisher(KafkaTemplate<String, ArenaMatchCompleted> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, ArenaMatchCompleted>> publish(ArenaMatchCompleted event) {

        // Use the match ID as the key so records for the same match stay ordered
        String matchId = event.matchId().toString();

        return kafkaTemplate.send(
                KafkaTopics.MATCH_COMPLETED,
                matchId,
                event);
    }

}
