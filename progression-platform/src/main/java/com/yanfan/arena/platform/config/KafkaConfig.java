package com.yanfan.arena.platform.config;

import com.yanfan.arena.contract.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Declares the Kafka topics the platform needs.
// Spring Boot's KafkaAdmin creates them automatically at startup.
@Configuration
public class KafkaConfig {

    // Input topic: completed match events are consumed from here
    @Bean
    NewTopic matchCompletedTopic() {
        return new NewTopic(KafkaTopics.MATCH_COMPLETED, 1, (short) 1);
    }

    // Dead-letter topic: permanent failures are published here (used from Step 4)
    @Bean
    NewTopic matchCompletedDltTopic() {
        return new NewTopic(KafkaTopics.MATCH_COMPLETED_DLT, 1, (short) 1);
    }

}
