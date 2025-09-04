package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import com.ldsilver.chingoohaja.validation.MatchingValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMatchingQueueService {

    private final StringRedisTemplate redisTemplate;

    public EnqueueResult enqueueUser(Long userId, Long categoryId, String queueId) {
        log.debug("매칭 대기열 참가 - userId: {}, categoryId: {}", userId, categoryId);

        long timestamp = Instant.now().toEpochMilli();
        double score = timestamp + (Math.random() * 1000);

        String userQueueValue = RedisMatchingConstants.KeyBuilder.userQueueValue(
                categoryId, queueId, timestamp
        );

        List<String> keys = Arrays.asList(
                RedisMatchingConstants.KeyBuilder.queueKey(categoryId),
                RedisMatchingConstants.KeyBuilder.userQueueKey(userId),
                RedisMatchingConstants.KeyBuilder.lockKey(userId)
        );

        List<String> args = Arrays.asList(
                userId.toString(),
                String.valueOf(score),
                userQueueValue,
                String.valueOf(MatchingValidationConstants.Queue.DEFAULT_TTL_SECONDS)
        );

        try {
            RedisScript<List> script = RedisScript.of(
                    RedisMatchingConstants.LuaScripts.JOIN_QUEUE, List.class
            );

            List<Object> result = redisTemplate.execute(script, keys, args.toArray());

            if (result != null && !result.isEmpty()) {
                Integer success = Integer.parseInt(result.get(0).toString());
                String message = result.get(1).toString();

                if (success == 1 && result.size() > 2) {
                    Integer position = Integer.parseInt(result.get(2).toString());
                    log.debug("매칭 대기열 참가 성공 - userId: {}, position: {}", userId, position);
                    return new EnqueueResult(false, message, null);
                } else {
                    log.debug("매칭 대기열 참가 실패 - userId: {}, reason: {}", userId, message);
                    return new EnqueueResult(false, message, null);
                }
            }

            return new EnqueueResult(false, RedisMatchingConstants.ResponseMessage.UNKNOWN_ERROR, null);

        } catch (Exception e) {
            log.error("매칭 대기열 참가 실패 - userId: {}", userId, e);
            return new EnqueueResult(false, RedisMatchingConstants.ResponseMessage.REDIS_ERROR, null);
        }
    }

    /**
     * 하이브리드 매칭 실행 (랜덤 80% (빠른 매칭) + 대기순 20%(공정성))
     */
    public MatchResult findMatchesHybrid(Long categoryId, int matchCount) {
        log.debug("하이브리드 매칭 실행 - categoryId: {}, matchCount: {}", categoryId, matchCount);
        try {
            boolean userWaitOrder = Math.random() < (1 - MatchingValidationConstants.Queue.RANDOM_MATCH_RATIO);

            List<String> keys = Arrays.asList(
                    RedisMatchingConstants.KeyBuilder.queueKey(categoryId)
            );

            List<String> args = Arrays.asList(
                    String.valueOf(matchCount),
                    userWaitOrder ? "1" : "0"
            );

            RedisScript<List> script = RedisScript.of(
                    RedisMatchingConstants.LuaScripts.FIND_MATCHES, List.class);

            List<Object> result = redisTemplate.execute(script, keys, args.toArray());

            if (result != null && !result.isEmpty()) {
                Integer success = Integer.parseInt(result.get(0).toString());
                String message = result.get(1).toString();

                if (success == 1 && result.size() > 2) {
                    List<Long> userIds = result.subList(2, result.size()).stream()
                            .map(obj -> Long.valueOf(obj.toString()))
                            .toList();

                    cleanupMatchedUsers(categoryId, userIds);

                    String strategy = userWaitOrder ? "대기순" : "랜덤";
                    log.debug("{} 매칭 성공 - categoryId: {}, users: {}", strategy, categoryId, userIds);
                    return new MatchResult(true, message, userIds);
                } else {
                    log.debug("매칭 실패 - categoryId: {}, reason: {}", categoryId, message);
                    return new MatchResult(false, message, Collections.emptyList());
                }
            }
            return new MatchResult(false, RedisMatchingConstants.ResponseMessage.UNKNOWN_ERROR, Collections.emptyList());
        } catch (Exception e) {
            log.error("하이브리드 매칭 실패 - categoryId: {}", categoryId, e);
            return new MatchResult(false, RedisMatchingConstants.ResponseMessage.REDIS_ERROR, Collections.emptyList());
        }
    }

    /**
     * 매칭된 사용자들의 메타 데이터 정리 (Lua 스크립트)
     * 정리 대상:
     * - user:queued:{userId}: 키 삭제
     * - queue:meta:{cat:categoryId}:queueId 해시 삭제
     * - 혹시 남아있을 ZSET 엔트리 삭제
     */
    private void cleanupMatchedUsers(Long categoryId, List<Long> userIds) {
        if (userIds.isEmpty()) return;

        try {
            RedisScript<Long> script = RedisScript.of(
                    RedisMatchingConstants.LuaScripts.CLEANUP_MATCHED_USERS,
                    Long.class
            );

            List<String> keys = RedisMatchingConstants.KeyBuilder.getCleanupKeys(categoryId, userIds);

            List<String> args = new ArrayList<>();
            args.add(categoryId.toString());
            args.add(String.valueOf(userIds.size()));
            userIds.forEach(id -> args.add(id.toString()));
            userIds.forEach(id -> {
                String qid = redisTemplate.opsForValue().get(RedisMatchingConstants.KeyBuilder.userQueueKey(id, categoryId));
                args.add(qid != null ? qid : "");
            });

            Long cleanedCount = redisTemplate.execute(script, keys, args.toArray());

            log.debug("매칭된 사용자 정리 완료 - categoryId: {}, cleaned: {}",categoryId, cleanedCount);
        } catch (Exception e) {
            log.error("매칭된 사용자 정리 실패 - categoryId: {}, userIds: {}", categoryId, userIds, e);
        }
    }

    public DequeueResult dequeueUser(Long userId, Long categoryId) {
        log.debug("매칭 대기열 탈퇴 - userId: {}, categoryId: {}", userId, categoryId);
        List<String> keys = Arrays.asList(
                RedisMatchingConstants.KeyBuilder.queueKey(categoryId),
                RedisMatchingConstants.KeyBuilder.userQueueKey(userId),
                RedisMatchingConstants.KeyBuilder.lockKey(userId)
        );

        List<String> args = Arrays.asList(userId.toString());

        try {
            RedisScript<List> script = RedisScript.of(
                    RedisMatchingConstants.LuaScripts.LEAVE_QUEUE, List.class
            );

            List<Object> result = redisTemplate.execute(script, keys, args.toArray());

            if (result != null && !result.isEmpty()) {
                Integer success  = Integer.parseInt(result.get(0).toString());
                String message = result.get(1).toString();

                boolean isSuccess = success == 1;
                log.debug("매칭 대기열 탈퇴 {} - userId: {}, reason: {}", isSuccess ? "성공" : "실패", userId, message);

                return new DequeueResult(isSuccess, message);
            }
            return new DequeueResult(false, RedisMatchingConstants.ResponseMessage.UNKNOWN_ERROR);

        } catch (Exception e) {
            log.error("매칭 큐 탈퇴 실패 - userId: {}", userId, e);
            return new DequeueResult(false, RedisMatchingConstants.ResponseMessage.REDIS_ERROR);
        }
    }


    public QueueStatusInfo getQueueStatus(Long userId) {
        String legacyUserQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);
        String queueId = redisTemplate.opsForValue().get(legacyUserQueueKey);

        if (queueId != null) {
            Long categoryId = RedisMatchingConstants.KeyBuilder.parseCategoryIdFromQueueId(queueId);
            if (categoryId != null) {
                String correctUserQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId, categoryId);
            }
        }

        try {
            String queueMetaKey = RedisMatchingConstants.KeyBuilder.queueMetaKey(queueId);
            Map<Object, Object> metaData = redisTemplate.opsForHash().entries(queueMetaKey);

            if (metaData.isEmpty()) {
                return null;
            }

            String categoryIdStr = (String) metaData.get("categoryId");
            Long categoryId = Long.valueOf(categoryIdStr);

            String ttlStr = (String) metaData.get("ttl");
            if (ttlStr != null) {
                long expireTime = Long.parseLong(ttlStr);
                if (Instant.now().getEpochSecond() > expireTime) {
                    dequeueUser(userId, categoryId);
                    return null;
                }
            }

            // 대기열에서 위치 조회
            String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);
            Long rank = redisTemplate.opsForZSet().rank(waitQueueKey, userId.toString());
            Long totalWaiting = redisTemplate.opsForZSet().zCard(waitQueueKey);

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

    public long getWaitingCount(Long categoryId) {
        String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);

        try {
            Long count = redisTemplate.opsForZSet().zCard(waitQueueKey);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("대기 인원 수 조회 실패 - categoryId: {}", categoryId, e);
            return 0L;
        }
    }


    // Result Classes
    public record EnqueueResult(boolean success, String message, Integer position) {}
    public record DequeueResult(boolean success, String message) {}
    public record MatchResult(boolean success, String message, List<Long> userIds) {}
    public record QueueStatusInfo(String queueId, Long categoryId, Integer position, Integer totalWaiting) {}

    private Long parseCategoryIdFromQueueId(String queueId) {
        // 예: queue_123_45_ab12cd34  => categoryId = 45
        try {
            String[] parts = queueId.split("_");
            if (parts.length >= 4) {
                return Long.parseLong(parts[2]);
            }
            return null;
        } catch (Exception e) {
            log.error("queueId에서 categoryId 파싱 실패");
            return null;
        }
    }
}

