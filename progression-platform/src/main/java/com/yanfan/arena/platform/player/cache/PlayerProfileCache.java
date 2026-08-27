package com.yanfan.arena.platform.player.cache;

import com.yanfan.arena.platform.player.api.PlayerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

// Cache current player profiles in Redis and allow MySQL fallback on cache failure.
@Component
public class PlayerProfileCache {

    private static final Logger log = LoggerFactory.getLogger(PlayerProfileCache.class);

    private static final String KEY_PREFIX = "player:profile:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final Duration ttl;

    @Autowired
    public PlayerProfileCache(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              @Value("${arena.cache.player-profile-ttl}") Duration ttl)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    // Read the cached JSON, an absent key means a cache miss
    public Optional<PlayerResponse> find(Long playerId) {
        try {
            String json = redisTemplate.opsForValue().get(key(playerId));

            if (json == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(objectMapper.readValue(json, PlayerResponse.class));
        }
        catch (DataAccessException | JacksonException exception) {
            // Treat Redis or JSON failure as a miss
            log.warn(
                    "Failed to read player profile cache: playerId={} cause={}",
                    playerId,
                    exception.getClass().getSimpleName()
            );

            return Optional.empty();
        }
    }

    // Store the player profile with the configured TTL
    public void put(PlayerResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);

            redisTemplate.opsForValue().set(
                    key(response.playerId()),
                    json,
                    ttl
            );
        }
        catch (DataAccessException | JacksonException exception) {
            log.warn(
                    "Failed to write player profile cache: playerId={} cause={}",
                    response.playerId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    // Remove stale profile after an update
    public void evict(Long playerId) {
        try {
            redisTemplate.delete(key(playerId));
        }
        catch (DataAccessException exception) {
            log.warn(
                    "Player profile cache eviction failed playerId={} cause={}",
                    playerId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    // Clear cached profiles so future reads reload current MySQL data
    public boolean clearAll() {
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions()
                        .match(KEY_PREFIX + "*")
                        .build())
            )
        {
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
            }

            return true;
        }
        catch (DataAccessException ex) {
            log.warn(
                    "Failed to clear player profile cache: cause={}",
                    ex.getClass().getSimpleName());

            return false;
        }
    }

    private String key(Long playerId) {
        return KEY_PREFIX + playerId;
    }

}
