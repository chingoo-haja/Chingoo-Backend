package com.ldsilver.chingoohaja.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallGracePeriodService {
    private static final String GRACE_PERIOD_PREFIX = "call:grace:";
    private static final long GRACE_PERIOD_SECONDS = 30; // 30초 유예

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 통화 참가자가 일시적으로 연결 끊김 상태로 표시
     */
    public void markUserDisconnected(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;
        redisTemplate.opsForValue().set(
                key,
                System.currentTimeMillis(),
                GRACE_PERIOD_SECONDS,
                TimeUnit.SECONDS
        );
        log.info("유예 기간 시작 - callId: {}, userId: {}, duration: {}초",
                callId, userId, GRACE_PERIOD_SECONDS);
    }

    /**
     * 통화 참가자가 재접속하여 유예 기간 해제
     */
    public void clearGracePeriod(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;
        redisTemplate.delete(key);
        log.info("유예 기간 해제 - callId: {}, userId: {}", callId, userId);
    }

    /**
     * 유예 기간 중인지 확인
     */
    public boolean isInGracePeriod(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 남은 유예 시간 (초)
     */
    public long getRemainingGracePeriod(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire : 0;
    }

    /**
     * 양쪽 참가자 모두 유예 기간이 끝났는지 확인
     */
    public boolean areBothUsersDisconnected(Long callId, Long user1Id, Long user2Id) {
        return !isInGracePeriod(callId, user1Id) &&
                !isInGracePeriod(callId, user2Id);
    }
}
