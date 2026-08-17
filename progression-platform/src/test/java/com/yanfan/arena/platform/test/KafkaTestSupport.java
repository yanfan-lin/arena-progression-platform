package com.yanfan.arena.platform.test;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Shared Kafka consumer helpers for integration tests
public final class KafkaTestSupport {

    private static final String DLT_TOPIC = "arena-match-completed-dlt";

    private KafkaTestSupport() {
    }

    // Wait for a dead-letter record with the expected key and original-topic header
    public static ConsumerRecord<String, byte[]> awaitDltRecord(KafkaContainer kafka,
                                                                 String groupId,
                                                                 String expectedKey,
                                                                 Duration timeout) {
        // Start at the earliest offset because each check uses a new consumer group
        Map<String, Object> config = consumerConfig(kafka, groupId, true);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(DLT_TOPIC));

            long deadline = System.currentTimeMillis() + timeout.toMillis();

            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, byte[]> record : records) {
                    if (expectedKey.equals(record.key())
                            && record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC) != null) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError("No dead-letter record arrived");
    }

    // Read the consumer group's committed offset, or return null when none is committed
    public static Long committedOffset(KafkaContainer kafka,
                                       String groupId,
                                       String topic,
                                       int partition) {
        Map<String, Object> config = consumerConfig(kafka, groupId, false);
        TopicPartition topicPartition = new TopicPartition(topic, partition);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
            OffsetAndMetadata offsetAndMetadata = consumer.committed(
                            Set.of(topicPartition),
                            Duration.ofSeconds(5))
                    .get(topicPartition);

            return offsetAndMetadata == null ? null : offsetAndMetadata.offset();
        }
    }

    // Build the common byte-consumer configuration used by Kafka integration tests
    private static Map<String, Object> consumerConfig(KafkaContainer kafka,
                                                      String groupId,
                                                      boolean readFromEarliest) {
        if (readFromEarliest) {
            return Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ConsumerConfig.GROUP_ID_CONFIG, groupId,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
            );
        }

        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );
    }
}
