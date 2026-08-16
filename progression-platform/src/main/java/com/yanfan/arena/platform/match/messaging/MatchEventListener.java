package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import com.yanfan.arena.platform.match.processing.MatchProcessingResult;
import com.yanfan.arena.platform.match.processing.MatchProcessor;
import com.yanfan.arena.platform.match.validation.MatchEventValidationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Receive raw records for completed match events
@Component
public class MatchEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchEventListener.class);

    private final MatchProcessor matchProcessor;

    @Autowired
    public MatchEventListener(MatchProcessor matchProcessor) {
        this.matchProcessor = matchProcessor;
    }

    @KafkaListener(topics = KafkaTopics.MATCH_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onMatchEvent(ConsumerRecord<String, ArenaMatchCompleted> record) {
        // The value is a completed match event
        ArenaMatchCompleted event = record.value();

        // Reject a missing event before reading its fields
        if (event == null) {
            throw new MatchEventValidationException("Match event is missing");
        }

        // Reject a missing match ID before comparing it to the Kafka key
        if (event.matchId() == null) {
            throw new MatchEventValidationException("Match event ID is missing");
        }

        // Reject a record whose key does not match the event ID
        if (!event.matchId().toString().equals(record.key())) {
            throw new MatchEventValidationException("Kafka key does not match the match event ID");
        }

        // Pass the event to the processor
        MatchProcessingResult result = matchProcessor.process(event);

        LOGGER.info("Processed match event {} outcome={}", event.eventId(), result.outcome());
    }


}
