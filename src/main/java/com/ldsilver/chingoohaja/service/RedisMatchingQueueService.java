package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.matching.MatchingConstants;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMatchingQueueService {

    private final StringRedisTemplate redisTemplate;

    public EnqueueResult enqueueUser(Long userId, Long categoryId, String queueId) {
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
        String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);
        String queueMetaKey = RedisMatchingConstants.KeyBuilder.queueMetaKey(queueId);
        String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);

        double score = Instant.now().toEpochMilli() + Math.random() * 100;
        long ttlSeconds = MatchingConstants.Queue.DEFAULT_TTL_SECONDS;

        try {
            // 1. SET NX EX로 중복 입장 레이스 방지 (선점)
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(userQueueKey, queueId, Duration.ofSeconds(ttlSeconds));

            if (!Boolean.TRUE.equals(lockAcquired)) {
                return new EnqueueResult(false, "ALREADY_IN_QUEUE", null);
            }

            // 2. 랜덤 매칭풀에 추가
            redisTemplate.opsForSet().add(queueKey, userId.toString());
            // 3. 대기 시간 기반 우선순위 큐에 추가
            redisTemplate.opsForZSet().add(waitQueueKey, userId.toString(), score);

            // 4. 큐 메타데이터 저장
            Map<String, String> metaData = Map.of(
                "userId", userId.toString(),
                "categoryId", categoryId.toString(),
                "queueId", queueId,
                "createdAt", String.valueOf(score),
                "ttl", String.valueOf(Instant.now().getEpochSecond() + ttlSeconds)
            );

            redisTemplate.opsForHash().putAll(queueMetaKey, metaData);
            redisTemplate.expire(queueMetaKey, Duration.ofSeconds(ttlSeconds));

            // 5. 위치 계산
            Long rank = redisTemplate.opsForZSet().rank(waitQueueKey, userId.toString());
            Integer position = rank != null ? rank.intValue() + 1 : 1;

            log.debug("매칭 큐 참가 성공 - userId: {}, position: {}", userId, position);
            return new EnqueueResult(true, "SUCCESS", position);

        } catch (Exception e) {
            redisTemplate.delete(userQueueKey);
            redisTemplate.opsForSet().remove(queueKey, userId.toString());
            redisTemplate.opsForZSet().remove(waitQueueKey, userId.toString());

            log.error("Redis 매칭 큐 참가 실패 - userId: {}", userId, e);
            return new EnqueueResult(false, "REDIS_ERROR", null);
        }
    }


    public QueueStatusInfo getQueueStatus(Long userId) {
        String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);
        String queueId = (String) redisTemplate.opsForValue().get(userQueueKey);

        if (queueId == null) {return null;}

        String queueMetaKey = RedisMatchingConstants.KeyBuilder.queueMetaKey(queueId);
        Map<Object, Object> metaData = redisTemplate.opsForHash().entries(queueMetaKey);

        if (metaData.isEmpty()) {
            return null;
        }

        String categoryIdStr = (String) metaData.get("categoryId");
        Long categoryId = Long.valueOf(categoryIdStr);
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);

        try {
            // 대기열에서 위치 조회
            Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
            Long totalWaiting = redisTemplate.opsForZSet().zCard(queueKey);

            return new QueueStatusInfo(
                    queueId,
                    categoryId,
                    rank != null ? rank.intValue() + 1 : null,
                    totalWaiting != null ? totalWaiting.intValue() : 0
            );

        } catch (Exception e) {
            log.error("큐 상태 조회 실패 - userId: {}", userId, e);
            return null;
        }
    }


    // Result Classes
    public record EnqueueResult(boolean success, String message, Integer position) {}
    public record DequeueResult(boolean success, String message) {}
    public record MatchResult(boolean success, String message, List<Long> userIds) {}
    public record QueueStatusInfo(String queueId, Long categoryId, Integer position, Integer totalWaiting) {}
}

