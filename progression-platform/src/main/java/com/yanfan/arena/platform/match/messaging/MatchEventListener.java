package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.stereotype.Component;

// Receive raw records for completed match events
@Component
public class MatchEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchEventListener.class);

    @KafkaListener(topics = KafkaTopics.MATCH_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onMatchEvent(ConsumerRecord<String, Object> record) {

        // Malformed JSON arrives as a null value
        if (record.headers().lastHeader(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER) != null) {
            LOGGER.warn("Malformed match event on topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());

            // Route this record to the dead-letter topic
            return;
        }

        // The value is a completed match event
        ArenaMatchCompleted event = (ArenaMatchCompleted) record.value();

        LOGGER.info("Received match event {} for match {}", event.eventId(), event.matchId());
    }


}
