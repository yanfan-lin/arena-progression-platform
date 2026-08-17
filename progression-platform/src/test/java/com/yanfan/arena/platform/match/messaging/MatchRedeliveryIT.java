package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.kafkaContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerKafkaProperties;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static org.assertj.core.api.Assertions.assertThat;


// Verify that a redelivered match event is committed exactly once
// after the listener restarts from an uncommitted source offset.
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
@Testcontainers
class MatchRedeliveryIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @Container
    static final KafkaContainer KAFKA = kafkaContainer();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
        registerKafkaProperties(registry, KAFKA);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Value("${spring.kafka.consumer.group-id}")
    String groupId;

    @Test
    void uncommittedOffsetRedeliveryIsProcessedOnce() throws Exception {
        // Load players and teams for the match
        insertThreeVsThreePlayersAndTeams(jdbcTemplate);

        // Team 1 wins
        ArenaMatchCompleted event = threeVsThreeEvent(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L);

        String json = objectMapper.writeValueAsString(event);

        SendResult<String, String> sendResult = kafkaTemplate.send(
                        "arena-match-completed",
                        event.matchId().toString(),
                        json)
                .get(10, TimeUnit.SECONDS);


        String topic = sendResult.getRecordMetadata().topic();
        int partition = sendResult.getRecordMetadata().partition();
        long sourceOffset = sendResult.getRecordMetadata().offset();

        // First delivery commits MySQL
        awaitProcessedCount(jdbcTemplate, 1);
        assertSingleMatchState();

        // Simulate the crash window by stopping the listener,
        // and rewinding the group offset to the original record
        stopListener();
        resetCommittedOffset(topic, partition, sourceOffset);

        // Restart the same group so Kafka redelivers the original record
        startListener();

        // The redelivery is handled as a duplicate,
        // then the source offset advances
        awaitCommittedOffset(topic, partition, sourceOffset + 1);

        // Still within one match
        assertSingleMatchState();
    }

    // Assert that only one match and one set of progression change were written
    private void assertSingleMatchState() {
        assertThat(countRows(jdbcTemplate, "processed_events"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_team_results"))
                .isEqualTo(2);
        assertThat(countRows(jdbcTemplate, "match_participant_results"))
                .isEqualTo(6);

        Long alphaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?", Long.class, 101L);
        assertThat(alphaOneXp)
                .isEqualTo(650L);

        Integer alphaMatchesPlayed = jdbcTemplate.queryForObject(
                "SELECT matches_played FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaMatchesPlayed)
                .isEqualTo(1);

        Integer alphaWins = jdbcTemplate.queryForObject(
                "SELECT wins FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaWins)
                .isEqualTo(1);

    }

    // Start the listener and wait until it is running
    private void startListener() throws InterruptedException {
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        container.start();

        long deadline = System.currentTimeMillis() + 10_000;

        while (System.currentTimeMillis() < deadline) {
            if (container.isRunning()) {
                return;
            }

            Thread.sleep(200);
        }

        throw new AssertionError("Listener did not start");
    }

    // Stop the listener and wait until it is no longer running
    private void stopListener() throws InterruptedException {
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        container.stop();

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (!container.isRunning()) {
                return;
            }

            Thread.sleep(200);
        }

        throw new AssertionError("Listener did not stop");
    }

    // Poll until the consumer group's committed offset reaches the expected value
    private void awaitCommittedOffset(String topic, int partition, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;

        while (System.currentTimeMillis() < deadline) {
            Long committed = committedOffset(topic, partition);

            if (committed != null && committed == expected) {
                return;
            }

            Thread.sleep(200);
        }

        assertThat(committedOffset(topic, partition)).isEqualTo(expected);
    }

    // Read the consumer group's committed offset,
    // or return null when none is committed
    private Long committedOffset(String topic, int partition) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config);

        OffsetAndMetadata offsetAndMetadata = consumer.committed(
                        Set.of(new TopicPartition(topic, partition)),
                        Duration.ofSeconds(5))
                .get(new TopicPartition(topic, partition));

        consumer.close();

        return offsetAndMetadata == null ? null : offsetAndMetadata.offset();
    }

    // Rewind the group's committed offset back to the source record
    private void resetCommittedOffset(String topic, int partition, long offset) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config);

        TopicPartition topicPartition = new TopicPartition(topic, partition);

        consumer.commitSync(Map.of(topicPartition, new OffsetAndMetadata(offset)));

        consumer.close();
    }


}
