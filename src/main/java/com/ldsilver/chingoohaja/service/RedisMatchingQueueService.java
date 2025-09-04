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
     * 랜덤 매칭: SET에서 무작위로 사용자 선택
     */
    private MatchResult findMatchesRandom(Long categoryId, int matchCount) {
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
        String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);

        try {
            Long available = redisTemplate.opsForSet().size(queueKey);
            if (available == null || available < matchCount) {
                log.debug("랜덤 매칭 대기 인원 부족 - categoryId: {}, available: {}",
                        categoryId, available != null ? available : 0);
                return new MatchResult(false, RedisMatchingConstants.ResponseMessage.INSUFFICIENT_USERS, Collections.emptyList());
            }

            List<String> selectedUsers = redisTemplate.opsForSet().pop(queueKey, matchCount);

            List<Long> userIds = selectedUsers.stream()
                    .map(Long::valueOf)
                    .toList();

            // ZSET에서도 제거 (데이터 정합성 유지)
            for (String userId : selectedUsers) {
                redisTemplate.opsForZSet().remove(waitQueueKey, userId);
            }

            cleanupMatchedUsers(categoryId, userIds);

            log.debug("랜덤 매칭 성공 - categoryId: {}, users: {}", categoryId, userIds);
            return new MatchResult(true, RedisMatchingConstants.ResponseMessage.SUCCESS, userIds);
        } catch (Exception e) {
            log.error("랜덤 매칭 실패 - categoryId: {}", categoryId, e);
            return new MatchResult(false, RedisMatchingConstants.ResponseMessage.REDIS_ERROR, Collections.emptyList());
        }
    }


    /**
     * 대기순 매칭: Lua 스크립트로 원자적 처리
     * - ZRANGE로 선택 → 선택된 사용자들을 ZREM, SREM으로 제거
     */
    private MatchResult findMatchesByWaitTimeAtomic(Long categoryId, int matchCount) {
        String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);
        String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);

        try {
            RedisScript<List> script = RedisScript.of(
                    RedisMatchingConstants.LuaScripts.ATOMIC_MATCH_BY_WAIT,
                    List.class
            );

            List<String> keys = Arrays.asList(waitQueueKey, queueKey);
            List<Object> result = redisTemplate.execute(script, keys, String.valueOf(matchCount));

            if (result != null && !result.isEmpty()) {
                Integer success = Integer.parseInt(result.get(0).toString());
                String message = result.get(1).toString();

                if (success == 1 && result.size() > 2) {
                    List<Long> userIds = result.subList(2, result.size()).stream()
                            .map(obj -> Long.valueOf(obj.toString()))
                            .toList();

                    cleanupMatchedUsers(categoryId, userIds);

                    log.debug("대기순 매칭 성공 - categoryId: {}, users: {}",categoryId, userIds);
                    return new MatchResult(true, message, userIds);
                } else {
                    log.debug("대기순 매칭 인원 부족 - categoryId: {}, available: {}",
                            categoryId, result.size() > 2 ? result.get(2) : 0);
                    return new MatchResult(false, message, Collections.emptyList());
                }
            }
            return new MatchResult(false, RedisMatchingConstants.ResponseMessage.UNKNOWN_ERROR, Collections.emptyList());
        } catch (Exception e) {
            log.error("대기순 매칭 실패 - categoryId: {}", categoryId, e);
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
        String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId, categoryId);
        String queueId = redisTemplate.opsForValue().get(userQueueKey);

        if (queueId == null) {
            return new DequeueResult(false, RedisMatchingConstants.ResponseMessage.NOT_IN_QUEUE);
        }

        try {
            String queueMetaKey = RedisMatchingConstants.KeyBuilder.queueMetaKey(queueId);
            Map<Object, Object> metaData = redisTemplate.opsForHash().entries(queueMetaKey);

            if (metaData.isEmpty()) {
                // fallback: queueId에서 categoryId 파싱
                Long parsedCategoryId = parseCategoryIdFromQueueId(queueId);
                if (parsedCategoryId == null) {
                    return new DequeueResult(false, RedisMatchingConstants.ResponseMessage.QUEUE_NOT_FOUND);
                }
                String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(parsedCategoryId);
                String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(parsedCategoryId);
                redisTemplate.opsForSet().remove(queueKey, userId.toString());
                redisTemplate.opsForZSet().remove(waitQueueKey, userId.toString());
                redisTemplate.delete(userQueueKey);
                return new DequeueResult(true, RedisMatchingConstants.ResponseMessage.SUCCESS);
            }

//            String categoryIdStr = (String) metaData.get("categoryId");
//            Long categoryId = Long.valueOf(categoryIdStr);

            // 모든 큐에서 사용자 제거
            String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
            String waitQueueKey = RedisMatchingConstants.KeyBuilder.waitQueueKey(categoryId);

            redisTemplate.opsForSet().remove(queueKey, userId.toString());
            redisTemplate.opsForZSet().remove(waitQueueKey, userId.toString());
            redisTemplate.delete(userQueueKey);
            redisTemplate.delete(queueMetaKey);

            log.debug("매칭 큐 탈퇴 성공 - userId: {}", userId);
            return new DequeueResult(true, RedisMatchingConstants.ResponseMessage.SUCCESS);

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

