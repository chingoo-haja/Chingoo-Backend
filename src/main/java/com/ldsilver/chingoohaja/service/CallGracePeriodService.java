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

    private final RedisTemplate<String, String> redisTemplate;

    private static final String GRACE_PERIOD_PREFIX = "grace_period:";
    private static final int GRACE_PERIOD_SECONDS = 30;

    /**
     * 사용자 연결 끊김 표시 (30초 TTL)
     */
    public void markUserDisconnected(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;

        // 30초 TTL로 저장
        redisTemplate.opsForValue().set(
                key,
                String.valueOf(System.currentTimeMillis()),
                GRACE_PERIOD_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("유예 기간 시작 - callId: {}, userId: {}, 만료: {}초 후",
                callId, userId, GRACE_PERIOD_SECONDS);
    }

    /**
     * 통화 참가자가 재접속하여 유예 기간 해제
     */
    public void cancelGracePeriod(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("유예 기간 취소 (재연결) - callId: {}, userId: {}", callId, userId);
        }
    }

    /**
     * 유예 기간 중인지 확인
     */
    public boolean isInGracePeriod(Long callId, Long userId) {
        String key = GRACE_PERIOD_PREFIX + callId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
