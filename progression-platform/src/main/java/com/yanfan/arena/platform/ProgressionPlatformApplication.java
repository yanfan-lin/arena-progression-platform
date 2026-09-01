package com.yanfan.arena.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Backend entry point
@SpringBootApplication
@EnableScheduling
public class ProgressionPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProgressionPlatformApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // Share one lock so rebuilds cannot overwrite committed leaderboard updates
    @Bean
    public Lock teamLeaderboardProjectionLock() {
        return new ReentrantLock();
    }
}
