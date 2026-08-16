package com.yanfan.arena.platform.match.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Verify that malformed JSON goes to DLT
@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
public class MatchEventDltIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("arena")
            .withUsername("arena")
            .withPassword("arena-test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void malformedJsonIsPublishedToDlt() throws Exception {
        String badJson = "{not-valid-json";

        kafkaTemplate.send("arena-match-completed", "bad-key", badJson)
                .get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord();

        // Original key is preserved
        assertThat(dltRecord.key()).isEqualTo("bad-key");

        // Original raw bytes is preserved
        assertThat(new String(dltRecord.value(), StandardCharsets.UTF_8)).isEqualTo(badJson);

        // Original topic is recorded as a header
        byte[] originalTopic = dltRecord.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value();

        assertThat(new String(originalTopic, StandardCharsets.UTF_8))
                .isEqualTo("arena-match-completed");

        // The malformed event was never processed
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_events", Integer.class))
                .isZero();
    }

    private ConsumerRecord<String, byte[]> awaitDltRecord() throws Exception {

        // Throwaway consumer that reads the DLT
        Map<String, Object> consumerConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerConfig);
        consumer.subscribe(List.of("arena-match-completed-dlt"));

        ConsumerRecord<String, byte[]> found = null;

        // Give 15 sec for Kafka to deliver DLT record
        long deadline = System.currentTimeMillis() + 15_000;

        while (System.currentTimeMillis() < deadline) {
            // Keep polling until the expected DLT record appears.
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, byte[]> record : records) {
                // Spring's DLT recoverer adds this header, so it identifies a real DLT record.
                if (record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC) != null) {
                    found = record;
                    break;
                }
            }

            if (found != null) {
                break;
            }
        }

        consumer.close();

        if (found == null) {
            throw new AssertionError("No dead-letter record arrived");
        }

        return found;
    }


}
