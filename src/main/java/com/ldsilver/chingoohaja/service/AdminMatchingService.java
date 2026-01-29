package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingQueueHealthResponse;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * 매칭 큐 헬스 체크
     */
    public MatchingQueueHealthResponse checkMatchingHealth(Long categoryId) {
        log.info("매칭 큐 헬스 체크 시작 - categoryId: {}", categoryId);

        // 카테고리 검증
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // === Redis 상태 ===
        long redisWaiting = redisMatchingQueueService.getWaitingCount(categoryId);
        boolean redisAvailable = redisMatchingQueueService.isRedisAvailable();

        MatchingQueueHealthResponse.RedisQueueStatus redisStatus =
                new MatchingQueueHealthResponse.RedisQueueStatus(
                        redisWaiting,
                        redisAvailable
                );

        // === DB 상태 ===
        long dbWaiting = matchingQueueRepository.countByCategoryIdAndStatus(
                categoryId, QueueStatus.WAITING
        );
        long dbExpired = matchingQueueRepository.countByCategoryIdAndStatus(
                categoryId, QueueStatus.EXPIRED
        );

        boolean consistent = (redisWaiting == dbWaiting);
        long gap = Math.abs(redisWaiting - dbWaiting);

        MatchingQueueHealthResponse.DatabaseQueueStatus databaseStatus =
                new MatchingQueueHealthResponse.DatabaseQueueStatus(
                        dbWaiting,
                        dbExpired,
                        consistent,
                        gap
                );

        // === 헬스 상태 판정 ===
        MatchingQueueHealthResponse.HealthStatus healthStatus =
                determineHealthStatus(consistent, dbExpired, gap);

        // === 경고 메시지 ===
        List<String> warnings = buildWarnings(consistent, dbExpired, gap, redisAvailable);

        // === 응답 생성 ===
        MatchingQueueHealthResponse response = new MatchingQueueHealthResponse(
                new MatchingQueueHealthResponse.CategoryInfo(
                        categoryId, category.getName(), category.isActive()
                ),
                redisStatus,
                databaseStatus,
                healthStatus,
                warnings,
                LocalDateTime.now()
        );

        log.info("매칭 큐 헬스 체크 완료 - categoryId: {}, status: {}, warnings: {}",
                categoryId, healthStatus, warnings.size());

        return response;
    }

    // ========== Private Helper Methods ========== //

    /**
     * 헬스 상태 판정
     */
    private MatchingQueueHealthResponse.HealthStatus determineHealthStatus(
            boolean consistent, long dbExpired, long gap) {
        if (gap > 10 || dbExpired > 100) {
            return MatchingQueueHealthResponse.HealthStatus.CRITICAL;
        }
        if (!consistent || dbExpired > 0) {
            return MatchingQueueHealthResponse.HealthStatus.WARNING;
        }
        return MatchingQueueHealthResponse.HealthStatus.HEALTHY;
    }

    /**
     * 경고 메시지 생성
     */
    private List<String> buildWarnings(
            boolean consistent,
            long dbExpired,
            long gap,
            boolean redisAvailable
    ) {
        List<String> warnings = new ArrayList<>();

        if (!consistent) {
            warnings.add(String.format("Redis-DB 불일치 (gap: %d)", gap));
        }
        if (dbExpired > 0) {
            warnings.add(String.format("정리 필요한 EXPIRED 레코드 %d건", dbExpired));
        }
        if (dbExpired > 100) {
            warnings.add("⚠️ 과다 EXPIRED 레코드 - 즉시 정리 권장");
        }
        if (!redisAvailable) {
            warnings.add("❌ Redis 서버 연결 불가");
        }

        return warnings;
    }
}