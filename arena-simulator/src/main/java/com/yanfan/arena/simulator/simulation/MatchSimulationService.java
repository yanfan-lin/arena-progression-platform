package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.messaging.MatchEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Generate arena matches and wait for Kafka to accept them.
@Service
public class MatchSimulationService {

    private final MatchGenerator matchGenerator;

    private final MatchEventPublisher matchEventPublisher;

    @Autowired
    public MatchSimulationService(MatchGenerator matchGenerator, MatchEventPublisher matchEventPublisher) {
        this.matchGenerator = matchGenerator;
        this.matchEventPublisher = matchEventPublisher;
    }

    public ArenaMatchCompleted simulateMatch(MatchMode mode) {

        ArenaMatchCompleted event = matchGenerator.generateMatch(mode);

        // Wait for Kafka acknowledgement before reporting success
        matchEventPublisher.publish(event).join();

        return event;
    }

}
