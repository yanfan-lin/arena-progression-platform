package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import com.yanfan.arena.platform.match.processing.MatchProcessingResult;
import com.yanfan.arena.platform.match.processing.MatchProcessor;
import com.yanfan.arena.platform.match.validation.MatchEventValidationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

        // Add record details to logs for this delivery
        MDC.put("topic", record.topic());
        MDC.put("partition", Integer.toString(record.partition()));
        MDC.put("offset", Long.toString(record.offset()));

        try {
            // The value is a completed match event
            ArenaMatchCompleted event = record.value();

            if (event == null) {
                throw new MatchEventValidationException(
                        "Match event is missing");
            }

            // Add event IDs to match processing logs
            MDC.put("eventId", String.valueOf(event.eventId()));
            MDC.put("matchId", String.valueOf(event.matchId()));

            // Reject a missing match ID before comparing it to the Kafka key
            if (event.matchId() == null) {
                throw new MatchEventValidationException(
                        "Match event ID is missing");
            }

            // Reject a record whose key does not match the match ID
            if (!event.matchId().toString().equals(record.key())) {
                throw new MatchEventValidationException(
                        "Kafka key does not match the match event ID");
            }

            // Pass the event to the processor
            MatchProcessingResult result = matchProcessor.process(event);

            LOGGER.info(
                    "Processed match outcome={}",
                    result.outcome());
        }
        finally {
            // Remove the record details before handling another record
            MDC.clear();
        }
    }

}
