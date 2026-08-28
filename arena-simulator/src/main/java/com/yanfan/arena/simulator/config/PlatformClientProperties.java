package com.yanfan.arena.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

// Bind the platform address used by simulator HTTP requests
@ConfigurationProperties(prefix = "arena.simulator.platform")
public record PlatformClientProperties(URI baseUrl) {

}