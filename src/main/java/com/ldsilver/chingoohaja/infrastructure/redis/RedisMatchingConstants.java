package com.ldsilver.chingoohaja.infrastructure.redis;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RedisMatchingConstants {

    // Redis 키 prefix
    @UtilityClass
    public static class KeyPrefix {
        public static final String QUEUE_PREFIX = "matching:queue:";
        public static final String USER_QUEUE_PREFIX = "user:queued:";
        public static final String QUEUE_META_PREFIX = "queue:meta:";
        public static final String CATEGORY_PREFIX = "category:";
    }

    // Redis 키 생성 헬퍼
    @UtilityClass
    public static class KeyBuilder {
        public static String queueKey(Long categoryId) {
            return KeyPrefix.QUEUE_PREFIX + KeyPrefix.CATEGORY_PREFIX + categoryId;
        }

        public static String userQueueKey(Long userId) {
            return KeyPrefix.USER_QUEUE_PREFIX + userId;
        }

        public static String queueMetaKey(String queueId) {
            return KeyPrefix.QUEUE_META_PREFIX + queueId;
        }
    }

    // Lua 스크립트
    @UtilityClass
    public static class LuaScripts {

        // 매칭 대기열 참가
        public static final String ENQUEUE_SCRIPT = """
            local queueKey = KEYS[1]
            local userQueueKey = KEYS[2]
            local queueMetaKey = KEYS[3]
            local userId = ARGV[1]
            local queueId = ARGV[2]
            local categoryId = ARGV[3]
            local score = ARGV[4]
            local ttl = ARGV[5]
            
            -- 중복 등록 확인
            if redis.call('EXISTS', userQueueKey) == 1 then
                return {0, 'ALREADY_IN_QUEUE'}
            end
            
            -- 대기열에 추가 (ZSET)
            redis.call('ZADD', queueKey, score, userId)
            redis.call('EXPIRE', queueKey, ttl)
            
            -- 사용자별 큐 정보 저장 (중복 방지)
            redis.call('SET', userQueueKey, queueId, 'EX', ttl)
            
            -- 큐 메타데이터 저장
            redis.call('HMSET', queueMetaKey,
                'userId', userId,
                'categoryId', categoryId,
                'queueId', queueId,
                'createdAt', score)
            redis.call('EXPIRE', queueMetaKey, ttl)
            
            local position = redis.call('ZRANK', queueKey, userId)
            return {1, 'SUCCESS', position + 1}
            """;

        // 매칭 대기열 탈퇴
        public static final String DEQUEUE_SCRIPT = """
            local queueKey = KEYS[1]
            local userQueueKey = KEYS[2]
            local queueMetaKey = KEYS[3]
            local userId = ARGV[1]
            
            -- 존재 확인
            if redis.call('EXISTS', userQueueKey) == 0 then
                return {0, 'NOT_IN_QUEUE'}
            end
            
            -- 제거
            redis.call('ZREM', queueKey, userId)
            redis.call('DEL', userQueueKey)
            redis.call('DEL', queueMetaKey)
            
            return {1, 'SUCCESS'}
            """;

        // 매칭 후보 조회 및 제거
        public static final String MATCH_SCRIPT = """
            local queueKey = KEYS[1]
            local matchCount = tonumber(ARGV[1])
            
            -- 대기열에서 가장 오래된 사용자들 조회
            local users = redis.call('ZRANGE', queueKey, 0, matchCount - 1)
            
            if #users < matchCount then
                return {0, 'INSUFFICIENT_USERS', #users}
            end
            
            -- 선택된 사용자들을 대기열에서 제거
            for i = 1, matchCount do
                redis.call('ZREM', queueKey, users[i])
            end
            
            -- 사용자별 큐 정보 제거
            for i = 1, matchCount do
                local userQueueKey = 'user:queued:' .. users[i]
                local queueMetaKey = 'queue:meta:' .. redis.call('GET', userQueueKey) or ''
                redis.call('DEL', userQueueKey)
                redis.call('DEL', queueMetaKey)
            end
            
            return {1, 'SUCCESS', unpack(users)}
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
