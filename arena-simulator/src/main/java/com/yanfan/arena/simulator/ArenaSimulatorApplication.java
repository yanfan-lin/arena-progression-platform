package com.yanfan.arena.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

// Run the simulator that publishes completed matches to Kafka
@SpringBootApplication
@EnableScheduling
public class ArenaSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArenaSimulatorApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
