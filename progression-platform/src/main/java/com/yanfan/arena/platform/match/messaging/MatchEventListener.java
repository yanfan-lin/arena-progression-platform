package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Receive raw records for completed match events
@Component
public class MatchEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchEventListener.class);


    @KafkaListener(topics = KafkaTopics.MATCH_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onMatchEvent(ConsumerRecord<String, String> record) {
        // Prove that the event arrived and keep the metadata.
        LOGGER.info(
                "Received match event from topic={} partition={} offset={} key={} payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value()
        );

    }

}
