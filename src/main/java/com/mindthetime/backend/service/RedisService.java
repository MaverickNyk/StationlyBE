package com.mindthetime.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${redis.enabled}")
    private boolean redisEnabled;

    /**
     * Save an object to Redis with a specific TTL
     * 
     * @param key        Redis key
     * @param value      Object to store (will be serialized to JSON)
     * @param ttlSeconds TTL in seconds
     */
    public void save(String key, Object value, long ttlSeconds) {
        if (!redisEnabled) {
            log.trace("Redis is disabled. Skipping save for key: {}", key);
            return;
        }
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(key, jsonValue, Duration.ofSeconds(ttlSeconds));
                log.trace("Saved to Redis: key={}, ttl={}s", key, ttlSeconds);
            } else {
                redisTemplate.opsForValue().set(key, jsonValue);
                log.trace("Saved to Redis: key={} (infinite TTL)", key);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object for key: {}", key, e);
        }
    }

    /**
     * Save multiple objects to Redis using pipelining for performance
     * 
     * @param data       Map of key to object value
     * @param ttlSeconds TTL in seconds (applied to all keys)
     */
    public void saveAll(Map<String, Object> data, long ttlSeconds) {
        if (!redisEnabled) {
            log.trace("Redis is disabled. Skipping batch save for {} items", data != null ? data.size() : 0);
            return;
        }
        if (data == null || data.isEmpty())
            return;

        try {
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                data.forEach((key, value) -> {
                    try {
                        byte[] rawKey = redisTemplate.getStringSerializer().serialize(key);
                        byte[] rawValue = redisTemplate.getStringSerializer()
                                .serialize(objectMapper.writeValueAsString(value));
                        if (rawKey != null && rawValue != null) {
                            if (ttlSeconds > 0) {
                                connection.stringCommands().setEx(rawKey, ttlSeconds, rawValue);
                            } else {
                                connection.stringCommands().set(rawKey, rawValue);
                            }
                        }
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize object for batch key: {}", key, e);
                    }
                });
                return null;
            });
            log.debug("Batch saved {} items to Redis with ttl={}s (-1 means infinite)", data.size(), ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to execute pipelined Redis write", e);
        }
    }

    /**
     * Get an object from Redis
     * 
     * @param key       Redis key
     * @param valueType Class type to deserialize to
     * @return Deserialized object or null if not found
     */
    public <T> T get(String key, Class<T> valueType) {
        // if (!redisEnabled) {
        // log.trace("Redis is disabled. Skipping get for key: {}", key);
        // return null;
        // }
        try {
            String jsonValue = redisTemplate.opsForValue().get(key);
            if (jsonValue != null) {
                return objectMapper.readValue(jsonValue, valueType);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize object for key: {}", key, e);
        }
        return null;
    }

    /**
     * Delete a key from Redis
     * 
     * @param key Redis key
     */
    public void delete(String key) {
        if (!redisEnabled) {
            log.trace("Redis is disabled. Skipping delete for key: {}", key);
            return;
        }
        redisTemplate.delete(key);
        log.debug("Deleted from Redis: key={}", key);
    }
}
