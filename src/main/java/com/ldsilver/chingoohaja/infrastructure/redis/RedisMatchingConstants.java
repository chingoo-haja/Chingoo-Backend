package com.ldsilver.chingoohaja.infrastructure.redis;

import com.ldsilver.chingoohaja.dto.matching.UserQueueInfo;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class RedisMatchingConstants {

    // Redis 키 prefix
    @UtilityClass
    public static class KeyPrefix {
        public static final String QUEUE_PREFIX = "queue:";           // ZSET - 통합 대기열
        public static final String USER_QUEUE_PREFIX = "queue:user:"; // STRING - 사용자 참가 정보
        public static final String LOCK_PREFIX = "queue:lock:";       // STRING - 분산 락
    }

    // Redis 키 생성 헬퍼
    @UtilityClass
    public static class KeyBuilder {

        // 카테고리별 통합 대기열 키
        public static String queueKey(Long categoryId) {
            return KeyPrefix.QUEUE_PREFIX + categoryId;
        }

        // 사용자별 참가 정보 키
        public static String userQueueKey(Long userId) {
            return KeyPrefix.USER_QUEUE_PREFIX + userId;
        }

        // 분산 락 키
        public static String lockKey(Long userId) {
            return KeyPrefix.LOCK_PREFIX + userId;
        }

        public static String userQueueValue(Long categoryId, String queueId, long timestamp) {
            return categoryId + ":" + queueId + ":" + timestamp;
        }

        public static UserQueueInfo parseUserQueueValue(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }

            String[] parts = value.split(":");
            if (parts.length != 3) {return null;}

            try {
                Long categoryId = Long.valueOf(parts[0]);
                String queueId = parts[1];
                Long timestamp = Long.valueOf(parts[2]);
                return new UserQueueInfo(categoryId, queueId, timestamp);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public static Long parseCategoryIdFromQueueId(String queueId) {
            if (queueId != null && queueId.startsWith("queue_")) {
                String[] parts = queueId.split("_");
                if (parts.length >= 3) {
                    try {
                        return Long.valueOf(parts[2]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
            return null;
        }

        // Lua 스크립트용 키 목록 생성
        public static List<String> getScriptKeys(Long categoryId, List<Long> userIds) {
            List<String> keys = new ArrayList<>();

            keys.add(queueKey(categoryId));

            for (Long userId: userIds) {
                keys.add(userQueueKey(userId));
            }

            for (Long userId: userIds) {
                keys.add(lockKey(userId));
            }
            return keys;
        }
    }

    @UtilityClass
    public static class LuaScripts {

        // 매칭 대기열 참가
        public static final String JOIN_QUEUE = """
                local queueKey = KEYS[1]
                local userQueueKey = KEYS[2]
                local lockKey = KEYS[3]
                local userId = ARGV[1]
                local score = tonumber(ARGV[2])
                local userQueueValue = ARGV[3]
                local ttlSeconds = tonumber(ARGV[4])
               \s
                -- 1. 분산 락 획득 (중복 참가 방지)
                local lockAcquired = redis.call('SET', lockKey, '1', 'NX', 'EX', 30)
                if not lockAcquired then
                    return {0, 'ALREADY_IN_QUEUE', 0}
                end
               \s
                -- 2. 이미 다른 큐에 참가 중인지 확인
                local existingQueue = redis.call('GET', userQueueKey)
                if existingQueue then
                    redis.call('DEL', lockKey)  -- 락 해제
                    return {0, 'ALREADY_IN_QUEUE', 0}
                end
               \s
                -- 3. 대기열에 추가
                redis.call('ZADD', queueKey, score, userId)
               \s
                -- 4. 사용자 참가 정보 저장
                redis.call('SETEX', userQueueKey, ttlSeconds, userQueueValue)
               \s
                -- 5. 대기 순서 계산
                local rank = redis.call('ZRANK', queueKey, userId)
                local position = rank and (rank + 1) or 1
               \s
                -- 6. 락 해제
                redis.call('DEL', lockKey)
               \s
                return {1, 'SUCCESS', position}
               """;

        // 통합 매칭 처리 (랜덤 + 대기순 하이라이트)
        public static final String FIND_MATCHES = """
                local queueKey = KEYS[1]
                local matchCount = tonumber(ARGV[1])
                local useWaitOrder = tonumber(ARGV[2])
               \s
                -- 1. 대기 인원 확인
                local availableCount = redis.call('ZCARD', queueKey)
                if availableCount < matchCount then
                    return {0, 'INSUFFICIENT_USERS', availableCount}
                end
               \s
                local selectedUsers = {}
               \s
                if useWaitOrder == 1 then
                    -- 대기순 매칭: 가장 오래 기다린 사용자들
                    selectedUsers = redis.call('ZRANGE', queueKey, 0, matchCount - 1)
                else
                    -- 랜덤 매칭: 무작위 선택
                    selectedUsers = redis.call('ZRANDMEMBER', queueKey, matchCount)
                end
               \s
                if #selectedUsers < matchCount then
                    return {0, 'INSUFFICIENT_USERS', #selectedUsers}
                end
               \s
                -- 2. 선택된 사용자들을 대기열에서 제거
                for i = 1, #selectedUsers do
                    redis.call('ZREM', queueKey, selectedUsers[i])
                end
               \s
                return {1, 'SUCCESS', unpack(selectedUsers)}
               """;

        // 대기열 탈퇴
        public static final String LEAVE_QUEUE = """
                local queueKey = KEYS[1]
                local userQueueKey = KEYS[2]
                local lockKey = KEYS[3]
                local userId = ARGV[1]
               \s
                -- 1. 참가 상태 확인
                local userQueueValue = redis.call('GET', userQueueKey)
                if not userQueueValue then
                    return {0, 'NOT_IN_QUEUE'}
                end
               \s
                -- 2. 분산 락 획득
                local lockAcquired = redis.call('SET', lockKey, '1', 'NX', 'EX', 10)
                if not lockAcquired then
                    return {0, 'LOCK_FAILED'}
                end
               \s
                -- 3. 대기열에서 제거
                local removed = redis.call('ZREM', queueKey, userId)
               \s
                -- 4. 사용자 참가 정보 삭제
                redis.call('DEL', userQueueKey)
               \s
                -- 5. 락 해제
                redis.call('DEL', lockKey)
               \s
                if removed == 1 then
                    return {1, 'SUCCESS'}
                else
                    return {0, 'NOT_IN_QUEUE'}
                end
               """;

        public static final String GET_QUEUE_STATUS = """
                local userQueueKey = KEYS[1]
                local userId = ARGV[1]
               \s
                -- 1. 사용자 참가 정보 조회
                local userQueueValue = redis.call('GET', userQueueKey)
                if not userQueueValue then
                    return {0, 'NOT_IN_QUEUE'}
                end
               \s
                -- 2. 참가 정보 파싱 
                local parts = {}
                for part in string.gmatch(userQueueValue, '([^:]+)') do
                    table.insert(parts, part)
                end
               \s
                if #parts ~= 3 then
                    return {0, 'INVALID_DATA'}
                end
               \s
                local categoryId = parts[1]
                local queueId = parts[2]
                local timestamp = tonumber(parts[3])
               \s
                -- 3. 대기열에서 위치 확인
                local queueKey = 'queue:' .. categoryId
                local rank = redis.call('ZRANK', queueKey, userId)
                local totalWaiting = redis.call('ZCARD', queueKey)
               \s
                local position = rank and (rank + 1) or nil
               \s
                return {1, 'SUCCESS', categoryId, queueId, timestamp, position, totalWaiting}
               \s
               """;

        public static final String CLEANUP_EXPIRED = """
                local queueKey = KEYS[1]
                local expiredTimestamp = tonumber(ARGV[1])
               \s
                -- 1. 만료된 사용자들 조회
                local expiredUsers = redis.call('ZRANGEBYSCORE', queueKey, '-inf', expiredTimestamp)
               \s
                if #expiredUsers == 0 then
                    return {1, 'NO_EXPIRED_USERS', 0}
                end
               \s
                -- 2. 만료된 사용자들 제거
                local removedCount = 0
                for i = 1, #expiredUsers do
                    local userId = expiredUsers[i]
                   \s
                    -- ZSET에서 제거
                    local removed = redis.call('ZREM', queueKey, userId)
                    if removed == 1 then
                        removedCount = removedCount + 1
                    end
                   \s
                    -- 사용자 큐 정보 제거
                    local userQueueKey = 'queue:user:' .. userId
                    redis.call('DEL', userQueueKey)
                end
               \s
                return {1, 'SUCCESS', removedCount}
               """;

        public static final String CLEANUP_MATCHED_USERS = """
                local queueKey = KEYS[1]
                local userCount = tonumber(ARGV[1])
               \s
                local cleanedCount = 0
               \s
                for i = 1, userCount do
                    local userId = ARGV[i + 1]
                    local userQueueKey = KEYS[i + 1]
                   \s
                    -- 사용자 큐 정보 제거
                    local deleted = redis.call('DEL', userQueueKey)
                    if deleted == 1 then
                        cleanedCount = cleanedCount + 1
                    end
                end
               \s
                return cleanedCount
               """;
    }

    // Redis 응답 메시지
    @UtilityClass
    public static class ResponseMessage {
        public static final String SUCCESS = "SUCCESS";
        public static final String ALREADY_IN_QUEUE = "ALREADY_IN_QUEUE";
        public static final String NOT_IN_QUEUE = "NOT_IN_QUEUE";
        public static final String INSUFFICIENT_USERS = "INSUFFICIENT_USERS";
        public static final String REDIS_ERROR = "REDIS_ERROR";
        public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
        public static final String QUEUE_NOT_FOUND = "QUEUE_NOT_FOUND";
    }
}
