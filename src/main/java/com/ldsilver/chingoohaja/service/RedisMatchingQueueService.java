package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.matching.MatchingConstants;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants.LuaScripts.ENQUEUE_SCRIPT;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMatchingQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    public EnqueueResult enqueueUser(Long userId, Long categoryId, String queueId) {
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
        String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);
        String queueMetaKey = RedisMatchingConstants.KeyBuilder.queueMetaKey(queueId);

        double score = Instant.now().getEpochSecond();
        long ttlSeconds = MatchingConstants.Queue.DEFAULT_TTL_SECONDS;

        try {
            RedisScript<List> script = RedisScript.of(ENQUEUE_SCRIPT, List.class);
            List<Object> result = redisTemplate.execute(script,
                    Arrays.asList(queueKey, userQueueKey, queueMetaKey),
                    userId.toString(), queueId, categoryId.toString(),
                    String.valueOf(score), String.valueOf(ttlSeconds));

            if (result != null && !result.isEmpty()) {
                Integer success = (Integer) result.get(0);
                String message = (String) result.get(1);

                if (success == 1 && result.size() > 2) {
                    Integer position = (Integer) result.get(2);
                    log.debug("사용자 매칭 큐 참가 성공 - userId: {}, categoryId: {}, position: {}",
                            userId, categoryId, position);
                    return new EnqueueResult(true, message, position);
                } else {
                    log.debug("사용자 매칭 큐 참가 실패 - userId: {}, reason: {}", userId, message);
                    return new EnqueueResult(false, message, null);
                }
            }
            return new EnqueueResult(false, "UNKNOWN_ERROR", null);
        } catch (Exception e) {
            log.error("Redis 매칭 큐 참가 실패 - userId: {}, categoryId: {}", userId, categoryId, e);
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

