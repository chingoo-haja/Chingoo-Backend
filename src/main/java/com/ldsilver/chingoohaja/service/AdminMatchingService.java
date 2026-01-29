package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMatchingService {

    private final RedisMatchingQueueService redisMatchingQueueService;
    private final MatchingQueueRepository matchingQueueRepository;
    private final CategoryRepository categoryRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * 특정 카테고리의 매칭 대기열 강제 정리
     */
    @Transactional
    public Map<String, Object> cleanupMatchingQueue(Long categoryId) {
        log.warn("⚠️ 매칭 큐 긴급 정리 시작 - categoryId: {}", categoryId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // 정리 전 현황
        long redisBeforeCount = redisMatchingQueueService.getWaitingCount(categoryId);
        long dbBeforeCount = matchingQueueRepository.countByCategoryIdAndStatus(
                categoryId, QueueStatus.WAITING
        );

        // Redis 정리
        String queueKey = "matching:queue:" + categoryId;
        Boolean redisDeleted = redisTemplate.delete(queueKey);

        // DB 정리
        int dbUpdated = matchingQueueRepository.updateExpiredQueues(
                QueueStatus.EXPIRED,
                LocalDateTime.now()
        );

        // 정리 후 현황
        long redisAfterCount = redisMatchingQueueService.getWaitingCount(categoryId);
        long dbAfterCount = matchingQueueRepository.countByCategoryIdAndStatus(
                categoryId, QueueStatus.WAITING
        );

        log.info("✅ 매칭 큐 정리 완료 - categoryId: {}, Redis: {} → {}, DB: {} → {}",
                categoryId, redisBeforeCount, redisAfterCount, dbBeforeCount, dbAfterCount);

        return Map.of(
                "categoryId", categoryId,
                "categoryName", category.getName(),
                "before", Map.of(
                        "redisWaiting", redisBeforeCount,
                        "dbWaiting", dbBeforeCount
                ),
                "after", Map.of(
                        "redisWaiting", redisAfterCount,
                        "dbWaiting", dbAfterCount
                ),
                "cleaned", Map.of(
                        "redisDeleted", redisDeleted != null && redisDeleted,
                        "dbUpdatedCount", dbUpdated
                ),
                "timestamp", LocalDateTime.now()
        );
    }
}