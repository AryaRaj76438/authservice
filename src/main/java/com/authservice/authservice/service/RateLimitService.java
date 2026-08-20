package com.authservice.authservice.service;

import com.authservice.authservice.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final RedisService redisService;

    public RateLimitResult check(String identifier, int maxRequests, Duration window){
        String key = RedisKeys.rateLimit(identifier);
        Long count = redisService.increment(key);

        if(count!=null && count==1){
            redisService.expire(key, window);
        }
        long remaining = Math.max(
                0,
                maxRequests-((count==null)?0:count)
        );

        if(count!=null && count>maxRequests){
            return new RateLimitResult(false, remaining, redisService.getTtl(key));
        }
        return new RateLimitResult(true, remaining, redisService.getTtl(key));
    }

    public record RateLimitResult(
            boolean allowed,
            long remaining,
            long retryAfter
    ) {
    }
}
