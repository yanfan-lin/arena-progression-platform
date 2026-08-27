package com.yanfan.arena.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Backend entry point
@SpringBootApplication
@EnableScheduling
public class ProgressionPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProgressionPlatformApplication.class, args);
    }
}
