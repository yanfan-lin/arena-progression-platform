package com.yanfan.arena.simulator.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.KafkaTopics;
import com.yanfan.arena.contract.MatchMode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Verify simulated match events reach Kafka with the expected key and payload.
@SpringBootTest
@Testcontainers
class SimulatorKafkaPublicationIT {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    // Connect simulator producer to the test Kafka broker
    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    MatchEventPublisher matchEventPublisher;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publishesMatchWithMatchIdKeyAndPayload() throws Exception {

        ArenaMatchCompleted event = matchEvent();

        Map<String, Object> consumerProps = Map.of(

                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),

                ConsumerConfig.GROUP_ID_CONFIG, "simulator-publication-" + UUID.randomUUID(),

                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",

                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,

                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {

            consumer.subscribe(List.of(KafkaTopics.MATCH_COMPLETED));

            // Wait for Kafka to acknowledge the published event
            matchEventPublisher.publish(event)
                    .get(10, TimeUnit.SECONDS);

            // Read the published record
            ConsumerRecord<String, byte[]> record =
                    consumer.poll(Duration.ofSeconds(10))
                            .iterator()
                            .next();

            // Convert the received JSON back into the shared event contract
            ArenaMatchCompleted receivedEvent =
                    objectMapper.readValue(record.value(),
                            ArenaMatchCompleted.class);

            assertThat(record.key())
                    .isEqualTo(event.matchId().toString());

            assertThat(receivedEvent)
                    .isEqualTo(event);
        }
    }

    // Create a match event for exact payload comparison
    private ArenaMatchCompleted matchEvent() {

        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-01-01T12:00:00Z"),
                1L,
                List.of(
                        new ArenaMatchCompleted.Team(
                                1L,
                                List.of(
                                        new ArenaMatchCompleted.Player(101L, 1, 0, 1),
                                        new ArenaMatchCompleted.Player(102L, 1, 0, 1),
                                        new ArenaMatchCompleted.Player(103L, 1, 0, 1)
                                )),
                        new ArenaMatchCompleted.Team(
                                2L,
                                List.of(
                                        new ArenaMatchCompleted.Player(201L, 0, 1, 0),
                                        new ArenaMatchCompleted.Player(202L, 0, 1, 0),
                                        new ArenaMatchCompleted.Player(203L, 0, 1, 0)
                                ))
                )
        );
    }

}
