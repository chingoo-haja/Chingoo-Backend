package com.ldsilver.chingoohaja.infrastructure.redis;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class RedisMatchingConstants {

    // Redis 키 prefix
    @UtilityClass
    public static class KeyPrefix {
        public static final String QUEUE_PREFIX = "matching:queue:{cat}:";
        public static final String WAIT_QUEUE_PREFIX = "wait:z:{cat}:";
        public static final String USER_QUEUE_PREFIX = "user:queued:{user}:";
        public static final String QUEUE_META_PREFIX = "queue:meta:{cat}:";
    }

    // Redis 키 생성 헬퍼
    @UtilityClass
    public static class KeyBuilder {
        // 랜덤 매칭용 SET (해시태그로 동일 슬롯 보장)
        public static String queueKey(Long categoryId) {
            return KeyPrefix.QUEUE_PREFIX.replace("{cat}", categoryId.toString());
        }

        // 대기 순서 우선순위 ZSET
        public static String waitQueueKey(Long categoryId) {
            return KeyPrefix.WAIT_QUEUE_PREFIX.replace("{cat}", categoryId.toString());
        }

        public static String userQueueKey(Long userId) {
            return KeyPrefix.USER_QUEUE_PREFIX.replace("{user}", userId.toString());
        }

        public static String queueMetaKey(String queueId) {
            // queueId 형식: queue_{userId}_{categoryId}_{random}
            String[] parts = queueId.split("_");
            if (parts.length >= 3) {
                String categoryId = parts[2];
                return KeyPrefix.QUEUE_META_PREFIX.replace("{cat}", categoryId) + queueId;
            }
            return "queue:meta:" + queueId;
        }

        // Lua 스크립트에서 사용할 키 목록 생성 (동일 해시태그)
        public static List<String> getCleanupKeys(Long categoryId, List<Long> userIds) {
            List<String> keys = new ArrayList<>();
            String hashTag = "{cat" + categoryId + "}";

            keys.add("cleanup" + hashTag); // 더미 키 (스크립트 요구사항)

            for (Long userId : userIds) {
                keys.add("user:queued:" + hashTag + ":" + userId);
                keys.add("queue:meta:" + hashTag + ":queue_" + userId + "_" + categoryId);
                keys.add("wait:z:" + hashTag + ":" + categoryId);
            }

            return keys;
        }
    }

    // Lua 스크립트
    @UtilityClass
    public static class LuaScripts {

        public static final String CLEANUP_MATCHED_USERS = """
            local userCount = tonumber(ARGV[1])
            
            for i = 1, userCount do
                local userId = ARGV[i + 1]
                local userQueueKey = 'user:queued:' .. userId
                local queueId = redis.call('GET', userQueueKey)
                
                if queueId then
                    local queueMetaKey = 'queue:meta:' .. queueId
                    redis.call('DEL', userQueueKey)
                    redis.call('DEL', queueMetaKey)
                end
            end
            
            return userCount
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
