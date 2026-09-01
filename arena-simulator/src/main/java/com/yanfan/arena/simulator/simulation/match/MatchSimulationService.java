package com.yanfan.arena.simulator.simulation.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import com.yanfan.arena.contract.MatchMode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

// Generate arena matches and publish them through Kafka.
@Service
public class MatchSimulationService {

    private final MatchGenerator matchGenerator;

    private final KafkaTemplate<String, ArenaMatchCompleted> kafkaTemplate;

    public MatchSimulationService(
            MatchGenerator matchGenerator,
            KafkaTemplate<String, ArenaMatchCompleted> kafkaTemplate) {
        this.matchGenerator = matchGenerator;
        this.kafkaTemplate = kafkaTemplate;
    }

    public ArenaMatchCompleted simulateMatch(MatchMode mode) {

        ArenaMatchCompleted event = matchGenerator.generateMatch(mode);

        // Keep events for the same match ordered and wait for Kafka before reporting success
        kafkaTemplate.send(
                        KafkaTopics.MATCH_COMPLETED,
                        event.matchId().toString(),
                        event)
                .join();

        return event;
    }

}
