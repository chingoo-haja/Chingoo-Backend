package com.ldsilver.chingoohaja.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 prefix
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String ACCESS_TOKEN_PREFIX = "access_token:";
    private static final String USER_TOKEN_PREFIX = "user_tokens:";

    public void storeRefreshToken(Long userId, String refreshToken, Duration expiration) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
        String userTokenKey = USER_TOKEN_PREFIX + userId;

        try {
            redisTemplate.opsForValue().set(tokenKey, userId, expiration);
            redisTemplate.opsForSet().add(userTokenKey, tokenKey);
            redisTemplate.expire(userTokenKey, expiration);
            log.debug("Refresh Token 캐시 저장 완료 - userId: {}, expiration: {}", userId, expiration);
        } catch (Exception e) {
            log.error("Refresh Token 캐시 저장 실패 - userId: {}", userId, e);
        }
    }

    public Long getUserIdByRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;

        try {
            Object userId = redisTemplate.opsForValue().get(key);
            if (userId != null) {
                log.debug("Refresh Token 캐시 조회 성공");
                return Long.valueOf(userId.toString());
            }
        } catch (Exception e) {
            log.error("Refresh Token 캐시 조회 실패 ", e);
        }

        return null;
    }

    public void deleteRefreshToken(String refreshToken) {
        String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;

        try {
            Object userId = redisTemplate.opsForValue().get(tokenKey);
            redisTemplate.delete(tokenKey);

            if (userId != null) {
                String userTokenKey = USER_TOKEN_PREFIX + userId;
                redisTemplate.opsForSet().remove(userTokenKey, tokenKey);
            }
        } catch (Exception e) {
            log.error("Refresh Token 캐시 삭제 실패 ", e);
        }
    }

    public void addToBlacklist(String accessToken, Duration expiration) {
        String key = ACCESS_TOKEN_PREFIX + "blacklist:" + accessToken;

        try {
            redisTemplate.opsForValue().set(key, "blacklisted", expiration);
            log.debug("Access Token 블랙리스트 추가");
        } catch (Exception e) {
            log.debug("Access Token 블랙리스트 추가 실패", e);
        }
    }

    public boolean isTokenBlacklisted(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + "blacklist:" + accessToken;

        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Access Token 블랙리스트 확인 실패", e);
            return false;
        }
    }

    public void deleteAllUserTokens(Long userId) {
        String userTokensKey = USER_TOKEN_PREFIX + userId;

        try {
            Set<Object> tokenKeys = redisTemplate.opsForSet().members(userTokensKey);

            if (tokenKeys != null && !tokenKeys.isEmpty()) {
                String[] keysArray = tokenKeys.stream()
                                .map(Object::toString)
                                .toArray(String[]::new);
                redisTemplate.delete(Arrays.asList(keysArray));
                redisTemplate.delete(userTokensKey);
            }
            log.debug("사용자의 모든 토큰 캐시 삭제 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("사용자 토큰 캐시 삭제 실패 - userId: {}", userId, e);
        }
    }

    public void extendTokenExpiration(String refreshToken, Duration newExpiration) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.expire(key, newExpiration);
                log.debug("토큰 만료 시간 연장, expiration: {}초", newExpiration.getSeconds());
            }
        } catch (Exception e) {
            log.error("토큰 만료 시간 연장 실패", e);
        }
    }

    public long getTokenTtl(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;

        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("토큰 TTL 조회 실패", e);
            return -1;
        }
    }

    public boolean isRedisAvailable() {
        try{
            redisTemplate.opsForValue().get("health_check");
            return true;
        } catch (Exception e) {
            log.error("Redis 연결 확인 실패", e);
            return false;
        }
    }
}
