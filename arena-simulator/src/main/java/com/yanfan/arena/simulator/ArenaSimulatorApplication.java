package com.yanfan.arena.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Run the simulator that publishes completed matches to Kafka
@ConfigurationPropertiesScan
@SpringBootApplication
public class ArenaSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArenaSimulatorApplication.class, args);
    }
}
