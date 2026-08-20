package com.authservice.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    // SET VALUE WITH TTL
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    // GET VALUE
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // DELETE
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // EXISTS
    public boolean exists(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    // INCREMENT
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    // SET EXPIRATION
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    // GET TTL
    public long getTtl(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null ? -1 : ttl;
    }
}