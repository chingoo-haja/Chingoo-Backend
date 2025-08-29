package com.ldsilver.chingoohaja.infrastructure.redis;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class RedisMatchingConstants {

    // Redis 키 prefix
    @UtilityClass
    public static class KeyPrefix {
        public static final String QUEUE_PREFIX = "matching:queue:";
        public static final String WAIT_QUEUE_PREFIX = "wait:z:";
        public static final String USER_QUEUE_PREFIX = "user:queued:";
        public static final String QUEUE_META_PREFIX = "queue:meta:";
    }

    // Redis 키 생성 헬퍼
    @UtilityClass
    public static class KeyBuilder {

        private static String catTag(Long categoryId) {return "{cat:" + categoryId + "}";}

        // 랜덤 매칭용 SET (해시태그로 동일 슬롯 보장)
        public static String queueKey(Long categoryId) {
            return KeyPrefix.QUEUE_PREFIX + catTag(categoryId);
        }

        // 대기 순서 우선순위 ZSET
        public static String waitQueueKey(Long categoryId) {
            return KeyPrefix.WAIT_QUEUE_PREFIX + catTag(categoryId) + ":" + categoryId;
        }

        public static String userQueueKey(Long userId) {
            return KeyPrefix.USER_QUEUE_PREFIX + userId + ":";
        }

        public static String queueMetaKey(String queueId) {
            // queueId 형식: queue_{userId}_{categoryId}_{random}
            Long categoryId = parseCategoryIdFromQueueId(queueId);
            if (categoryId != null) {
                return KeyPrefix.QUEUE_META_PREFIX + catTag(categoryId) + ":" + queueId;
            }
            return KeyPrefix.QUEUE_META_PREFIX + queueId;
        }

        // Lua 스크립트에서 사용할 키 목록 생성 (동일 해시태그)
        public static List<String> getCleanupKeys(Long categoryId, List<Long> userIds) {
            List<String> keys = new ArrayList<>();
            String tag = "{cat:" + categoryId + "}";

            keys.add("cleanup" + tag); // 더미 키 (스크립트 요구사항)
            keys.add("wait:z:" + tag + ":" + categoryId);

            for (Long userId : userIds) {
                keys.add("user:queued:" + userId + ":");
            }
            return keys;
        }

        private static Long parseCategoryIdFromQueueId(String queueId) {
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
            local waitQueueKey = 'wait:z:{cat:' .. categoryId .. '}:' .. categoryId
            local cleanedCount = 0
            
            for i = 1, userCount do
                local userId = ARGV[i + 2]
                local userQueueKey = 'user:queued:' .. userId .. ':'
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
}
