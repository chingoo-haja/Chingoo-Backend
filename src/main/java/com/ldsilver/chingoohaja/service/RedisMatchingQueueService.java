package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.dto.matching.UserQueueInfo;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import com.ldsilver.chingoohaja.validation.MatchingValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

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
                    return new EnqueueResult(true, message, position);
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
     */
    private void cleanupMatchedUsers(Long categoryId, List<Long> userIds) {
        if (userIds.isEmpty()) return;

        try {
            List<String> keys = new ArrayList<>();
            keys.add(RedisMatchingConstants.KeyBuilder.queueKey(categoryId));

            for (Long userId : userIds) {
                keys.add(RedisMatchingConstants.KeyBuilder.userQueueKey(userId));
            }

            List<String> args = new ArrayList<>();
            args.add(String.valueOf(userIds.size()));
            userIds.forEach(id -> args.add(id.toString()));

            RedisScript<Long> script = RedisScript.of(
                    RedisMatchingConstants.LuaScripts.CLEANUP_MATCHED_USERS, Long.class
            );

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
        log.debug("큐 상태 조회 - userId: {}", userId);

        try {
            // 1. 사용자 참가 정보 직접 조회
            String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);
            String userQueueValue = redisTemplate.opsForValue().get(userQueueKey);

            if (userQueueValue == null) {
                return null;
            }

            // 2. 참가 정보 파싱
            UserQueueInfo queueInfo = RedisMatchingConstants.KeyBuilder.parseUserQueueValue(userQueueValue);

            if (queueInfo == null) {
                log.warn("잘못된 큐 형식 - userId: {}, value: {}", userId, userQueueValue);

                redisTemplate.delete(userQueueKey);
                return null;
            }

            // 3. 만료 확인
            long currentTimestamp = Instant.now().toEpochMilli();
            if (queueInfo.isExpired(currentTimestamp, MatchingValidationConstants.Queue.DEFAULT_TTL_SECONDS)) {
                log.debug("만료된 큐 정보 발견 - userId: {}", userId);
                cleanupExpiredUser(userId, queueInfo.categoryId());
                return null;
            }


            // 4. 대기 순서 조회
            String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(queueInfo.categoryId());
            Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
            Long totalWaiting = redisTemplate.opsForZSet().zCard(queueKey);

            Integer position = rank != null ? rank.intValue() + 1  : null;
            Integer totalCount = totalWaiting != null ? totalWaiting.intValue() : 0;

            log.debug("큐 상태 조회 성공 - userId: {}, position: {}/{}", userId, position, totalCount);

            return new QueueStatusInfo(
                    queueInfo.queueId(),
                    queueInfo.categoryId(),
                    position,
                    totalCount
            );

        } catch (Exception e) {
            log.error("큐 상태 조회 실패 - userId: {}", userId, e);
            return null;
        }
    }

    public long getWaitingCount(Long categoryId) {
        try {
            String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
            Long count = redisTemplate.opsForZSet().zCard(queueKey);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("대기 인원 수 조회 실패 - categoryId: {}", categoryId, e);
            return 0L;
        }
    }

    public Map<Long, Long> getAllCategoryStats() {
        log.debug("전체 카테고리 통계 조회");

        Map<Long, Long> stats = new HashMap<>();

        try {
            // 카테고리 1-20 범위에서 활성 큐 확인
            for (long categoryId = 1; categoryId <= 20; categoryId++) {
                long waitingCount = getWaitingCount(categoryId);
                if (waitingCount > 0) {
                    stats.put(categoryId, waitingCount);
                }
            }

            log.debug("카테고리 통계 조회 완료 - 활성 카테고리 수: {}", stats.size());
            return stats;

        } catch (Exception e) {
            log.error("카테고리 통계 조회 실패", e);
            return Collections.emptyMap();
        }
    }


    private void cleanupExpiredUser(Long userId, Long categoryId) {
        try {
            String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
            String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(userId);

            redisTemplate.opsForZSet().remove(queueKey, userId.toString());
            redisTemplate.delete(userQueueKey);

            log.debug("만료된 사용자 정리 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("만료된 사용자 정리 실패 - userId: {}", userId, e);
        }
    }

    public boolean isRedisAvailable() {
        try {
            String result = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.error("Redis 연결 확인 실패, e");
            return false;
        }
    }



    // Result Classes
    public record EnqueueResult(boolean success, String message, Integer position) {}
    public record DequeueResult(boolean success, String message) {}
    public record MatchResult(boolean success, String message, List<Long> userIds) {}
    public record QueueStatusInfo(String queueId, Long categoryId, Integer position, Integer totalWaiting) {}
}

