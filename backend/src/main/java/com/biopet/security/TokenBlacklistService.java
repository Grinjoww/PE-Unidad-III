package com.biopet.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenBlacklistService {
    private static final String PREFIX = "jwt:blacklist:";
    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(PREFIX + jti, "revoked", ttl);
        }
    }

    public boolean isRevoked(String jti) {
        Boolean exists = redisTemplate.hasKey(PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }
}
