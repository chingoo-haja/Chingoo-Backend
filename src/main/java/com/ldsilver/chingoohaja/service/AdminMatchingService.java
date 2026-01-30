package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingQueueCleanupResponse;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingQueueHealthResponse;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
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
import java.util.Set;

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
    public MatchingQueueCleanupResponse cleanupMatchingQueue(Long categoryId) {
        log.warn("⚠️ 매칭 큐 긴급 정리 시작 - categoryId: {}", categoryId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        try {
            // 1. 정리 전 현황 기록
            long redisBeforeCount = redisMatchingQueueService.getWaitingCount(categoryId);
            long dbBeforeCount = matchingQueueRepository.countByCategoryIdAndStatus(
                    categoryId, QueueStatus.WAITING
            );

            log.info("정리 전 상태 - categoryId: {}, Redis: {}, DB WAITING: {}",
                    categoryId, redisBeforeCount, dbBeforeCount);

            // ✅ 2. 올바른 Redis 키 사용
            String queueKey = RedisMatchingConstants.KeyBuilder.queueKey(categoryId);
            log.info("정리할 Redis 키: {}", queueKey);

            // 2-1. 사용자 큐 메타데이터 정리
            Set<String> userIds = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            if (userIds != null && !userIds.isEmpty()) {
                log.info("대기열 사용자 IDs: {}", userIds);

                for (String userId : userIds) {
                    String userQueueKey = RedisMatchingConstants.KeyBuilder.userQueueKey(Long.parseLong(userId));
                    redisTemplate.delete(userQueueKey);
                    log.info("사용자 큐 삭제: {}", userQueueKey);
                }
            }

            // 2-2. 대기열 키 삭제
            Boolean redisDeleted = redisTemplate.delete(queueKey);
            log.info("Redis 대기열 삭제 결과: {}", redisDeleted);

            // ✅ 3. DB 정리 (해당 카테고리만)
            int dbUpdated = 0;

            // WAITING → EXPIRED (해당 카테고리만)
            int waitingToExpired = matchingQueueRepository.updateExpiredQueuesByCategory(
                    categoryId, QueueStatus.EXPIRED, LocalDateTime.now()
            );
            log.info("DB WAITING → EXPIRED: {}건 (categoryId: {})", waitingToExpired, categoryId);

            // EXPIRED 물리 삭제 (해당 카테고리만)
            List<MatchingQueue> categoryExpired = matchingQueueRepository.findExpiredQueuesByCategory(categoryId);

            if (!categoryExpired.isEmpty()) {
                matchingQueueRepository.deleteAll(categoryExpired);
                log.info("DB EXPIRED 물리 삭제: {}건 (categoryId: {})", categoryExpired.size(), categoryId);
            }

            dbUpdated = waitingToExpired + categoryExpired.size();

            // 4. 정리 후 현황 확인
            long redisAfterCount = redisMatchingQueueService.getWaitingCount(categoryId);
            long dbAfterCount = matchingQueueRepository.countByCategoryIdAndStatus(
                    categoryId, QueueStatus.WAITING
            );

            log.info("✅ 정리 완료 - Redis: {} → {}, DB: {} → {}",
                    redisBeforeCount, redisAfterCount, dbBeforeCount, dbAfterCount);

            return MatchingQueueCleanupResponse.of(
                    categoryId,
                    category.getName(),
                    redisBeforeCount,
                    dbBeforeCount,
                    redisAfterCount,
                    dbAfterCount,
                    Boolean.TRUE.equals(redisDeleted),
                    dbUpdated
            );

        } catch (Exception e) {
            log.error("❌ 매칭 큐 정리 실패 - categoryId: {}", categoryId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "매칭 큐 정리 중 오류 발생");
        }
    }

    /**
     * 매칭 큐 헬스 체크
     */
    public MatchingQueueHealthResponse checkMatchingHealth(Long categoryId) {
        log.debug("매칭 큐 헬스 체크 시작 - categoryId: {}", categoryId);

        // 카테고리 검증
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        // === Redis 상태 ===
        long redisWaiting = redisMatchingQueueService.getWaitingCount(categoryId);
        boolean redisAvailable = redisMatchingQueueService.isRedisAvailable();

        // === DB 상태 ===
        long dbWaiting = matchingQueueRepository.countByCategoryIdAndStatus(
                categoryId, QueueStatus.WAITING
        );
        long dbExpired = matchingQueueRepository.countByCategoryIdAndStatus(
                categoryId, QueueStatus.EXPIRED
        );

        boolean consistent = (redisWaiting == dbWaiting);
        long gap = Math.abs(redisWaiting - dbWaiting);

        // === 헬스 상태 판정 ===
        MatchingQueueHealthResponse.HealthStatus healthStatus =
                determineHealthStatus(consistent, dbExpired, gap);

        // === 경고 메시지 ===
        List<String> warnings = buildWarnings(consistent, dbExpired, gap, redisAvailable);

        log.info("매칭 큐 헬스 체크 완료 - categoryId: {}, status: {}, warnings: {}",
                categoryId, healthStatus, warnings.size());

        return MatchingQueueHealthResponse.of(
                categoryId,
                category.getName(),
                category.isActive(),
                redisWaiting,
                redisAvailable,
                dbWaiting,
                dbExpired,
                consistent,
                gap,
                healthStatus,
                warnings
        );
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