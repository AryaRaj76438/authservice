package com.authservice.authservice.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RedisKeys {

    private static final String PREFIX = "authservice:";

    public String loginAttempts(String email) {
        return PREFIX + "login:attempts:" + email;
    }

    public String loginLock(String email) {
        return PREFIX + "login:lock:" + email;
    }

    public String verificationCooldown(String email) {
        return PREFIX + "verification:cooldown:" + email;
    }

    public String rateLimit(String identifier) {
        return PREFIX + "rate-limit:" + identifier;
    }
}