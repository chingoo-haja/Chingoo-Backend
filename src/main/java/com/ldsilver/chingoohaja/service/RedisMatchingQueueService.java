package com.ldsilver.chingoohaja.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMatchingQueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_PREFIX = "matching_queue:category:";
    private static final String USER_QUEUE_PREFIX = "user_queue:";
    private static final String QUEUE_DATA_PREFIX = "queue_data:";

    private static final Duration QUEUE_EXPIRATION = Duration.ofMinutes(10);
    private static final int MAX_QUEUE_SIZE_PER_CATEGORY = 100;


    public String joinQueue(Long userId, Long categoryId, String categoryName) {
        String queueId = generateQueueId(userId, categoryId);
        String categoryQueueKey = QUEUE_PREFIX + categoryId;
        String userQueueKey = USER_QUEUE_PREFIX + userId;
        String queueDataKey = QUEUE_DATA_PREFIX + queueId;

        if (isUserInAnyQueue(userId)) {
            throw new CustomException(ErrorCode.ALREADY_IN_QUEUE);
        }

        Long queueSize = redisTemplate.opsForList().size(categoryQueueKey);
        if (queueSize != null && queueSize >= MAX_QUEUE_SIZE_PER_CATEGORY) {
            throw new CustomException(ErrorCode.MATCHING_QUEUE_FULL);
        }

        try {
            MatchingQueueData queueData = MatchingQueueData.of(
                    queueId, userId, categoryId, categoryName, LocalDateTime.now()
            );

            String queueDataJson = objectMapper.writeValueAsString(queueData);

            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 1. 카테고리별 큐에 추가
                    redisTemplate.opsForList().rightPush(categoryQueueKey, queueId);
                    redisTemplate.expire(categoryQueueKey, QUEUE_EXPIRATION);

                    // 2. 사용자-큐 매핑 저장
                    redisTemplate.opsForValue().set(userQueueKey, queueId, QUEUE_EXPIRATION);

                    // 3. 큐 상세 데이터 저장
                    redisTemplate.opsForValue().set(queueDataKey, queueDataJson, QUEUE_EXPIRATION);

                    return operations.exec();
                }
            });

            return queueId;

        } catch (JsonProcessingException e) {
            log.error("매칭 큐 데이터 직렬화 실패 - userId: {}", userId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }


    public int estimateWaitTime(Long userId, Long categoryId) {
        String userQueueKey = USER_QUEUE_PREFIX + userId;
        String queueId = (String) redisTemplate.opsForValue().get(userQueueKey);

        if (queueId == null) {
            return 0;
        }
        int queuePosition = getQueuePosition(queueId, categoryId);

        // 평균 매칭 시간 30초로 가정 (임시)
        return Math.max(0, (queuePosition -1) *30);
    }

    public int getQueuePosition(String queueId, Long categoryId) {
        String categoryQueueKey = QUEUE_PREFIX + categoryId;
        List<Object> queueIds = redisTemplate.opsForList().range(categoryQueueKey, 0, -1);
        if (queueIds == null) {return 0;}
        for (int i = 0; i < queueIds.size(); i++) {
            if (queueId.equals(queueIds.get(i).toString())) {
                return i+1;
            }
        }
        return 0;
    }


    private boolean isUserInAnyQueue(Long userId) {
        String userQueueKey = USER_QUEUE_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(userQueueKey));
    }

    private String generateQueueId(Long userId, Long categoryId) {
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        return String.format("queue_%d_%d_%d", userId, categoryId, timestamp);
    }


    public record MatchingQueueData(
            String queueId,
            Long userId,
            Long categoryId,
            String categoryName,
            LocalDateTime joinedAt
    ) {
        public static MatchingQueueData of(
                String queueId,
                Long userId,
                Long categoryId,
                String categoryName,
                LocalDateTime joinedAt
        ) {
            return new MatchingQueueData(queueId, userId, categoryId, categoryName, joinedAt);
        }
    }
}
