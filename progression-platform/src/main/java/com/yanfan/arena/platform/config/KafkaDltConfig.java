package com.yanfan.arena.platform.config;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

@Configuration
public class KafkaDltConfig {

    // Producer used only for dead-letter records
    @Bean
    KafkaTemplate<String, Object> dltKafkaTemplate(KafkaProperties properties) {
        Map<String, Object> producerProps = properties.buildProducerProperties();

        // Wait for all replicas to confirm each DLT write
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // Prevent duplicate DLT records when Kafka retries a send
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // byte[] keeps malformed JSON raw,
        // ArenaMatchCompleted is serialized as JSON
        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                ArenaMatchCompleted.class, new JacksonJsonSerializer<>()));

        ProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), valueSerializer);

        return new KafkaTemplate<>(producerFactory);
    }

    // Standard Kafka producer used to send ordinary messages to Kafka.
    @Bean
    KafkaTemplate<String, String> kafkaTemplate(KafkaProperties properties) {

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                properties.buildProducerProperties(),
                new StringSerializer(),
                new StringSerializer()));
    }

}
