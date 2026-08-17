package com.yanfan.arena.platform.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

//Integration test container settings
public final class IntegrationTestContainers {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.11");
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.1");

    private IntegrationTestContainers() {
    }

    // Create a MySQL container with the standard test database and credentials
    public static MySQLContainer mysqlContainer() {
        return new MySQLContainer(MYSQL_IMAGE)
                .withDatabaseName("arena")
                .withUsername("arena")
                .withPassword("arena-test");
    }

    // Create a Kafka container with the standard test image
    public static KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    // Register the MySQL container as Spring's datasource
    public static void registerMySqlProperties(DynamicPropertyRegistry registry, MySQLContainer container) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    // Register the Kafka container as Spring's broker
    public static void registerKafkaProperties(DynamicPropertyRegistry registry, KafkaContainer container) {
        registry.add("spring.kafka.bootstrap-servers", container::getBootstrapServers);
    }
}
