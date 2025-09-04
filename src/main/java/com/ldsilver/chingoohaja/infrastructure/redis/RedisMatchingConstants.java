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
    }

    // Lua 스크립트
    @UtilityClass
    public static class LuaScripts {

        // 대기순 매칭: 선정+제거 원자화 (경쟁 상황에서 중복 매칭 방지)
        public static final String ATOMIC_MATCH_BY_WAIT = """
            local waitQueueKey = KEYS[1]
            local randomQueueKey = KEYS[2]
            local matchCount = tonumber(ARGV[1])
            
            -- 가장 오래 기다린 사용자들 선택
            local users = redis.call('ZRANGE', waitQueueKey, 0, matchCount - 1)
            
            if #users < matchCount then
                return {0, 'INSUFFICIENT_USERS', #users}
            end
            
            -- 원자적으로 두 큐에서 모두 제거
            for i = 1, #users do
                redis.call('ZREM', waitQueueKey, users[i])
                redis.call('SREM', randomQueueKey, users[i])
            end
            
            return {1, 'SUCCESS', unpack(users)}
            """;

        // 매칭된 사용자들 정리 (ZSET 정리 포함, 올바른 반환 타입)
        public static final String CLEANUP_MATCHED_USERS = """
            local categoryId = ARGV[1]
            local userCount = tonumber(ARGV[2])
            
            -- 동적 키 조립 대신 KEYS 배열 사용
            local waitQueueKey = KEYS[2] -- getCleanupKeys에서 전달받은 waitQueueKey
            local cleanedCount = 0
            
            for i = 1, userCount do
                local userId = ARGV[i + 2]
                
                local userQueueKey = KEYS[2 + i]
                local queueId = ARGV[2 + userCount + i]  -- i번째 queueId 전달
                local queueMetaKey = 'queue:meta:{cat:' .. categoryId .. '}:' .. queueId
                
                -- 사용자 큐 정보 제거
                if redis.call('EXISTS', userQueueKey) == 1 then
                    redis.call('DEL', userQueueKey)
                    cleanedCount = cleanedCount + 1
                end
                
                -- 메타데이터 제거
                if redis.call('EXISTS', queueMetaKey) == 1 then
                    redis.call('DEL', queueMetaKey)
                end
                
                -- ZSET에서도 제거 (혹시 남아있을 경우)
                redis.call('ZREM', waitQueueKey, userId)
            end
            
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

    public record
}
