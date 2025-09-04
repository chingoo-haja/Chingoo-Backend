package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.matching.MatchingCategoryStats;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingResponse;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatusResponse;
import com.ldsilver.chingoohaja.infrastructure.redis.RedisMatchingConstants;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.validation.MatchingValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final RedisMatchingQueueService redisMatchingQueueService;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final MatchingQueueRepository matchingQueueRepository;

    @Transactional
    public MatchingResponse joinMatchingQueue(Long userId, MatchingRequest request) {
        log.debug("매칭 대기열 참가 시작 - userId: {}, categoryId: {}", userId, request.categoryId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.isActive()) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_ACTIVE);
        }

        RedisMatchingQueueService.QueueStatusInfo existingQueue = redisMatchingQueueService.getQueueStatus(userId);
        if (existingQueue != null) {
            throw new CustomException(ErrorCode.ALREADY_IN_QUEUE);
        }

        String queueId = generateQueueId(userId, request.categoryId());

        try {
            // DB에 매칭 큐 기록 (이력 관리용)
            MatchingQueue matchingQueue = MatchingQueue.from(user, category, QueueType.RANDOM_MATCH, queueId);
            MatchingQueue savedQueue = matchingQueueRepository.save(matchingQueue);

            // Redis 대기열에 참가
            RedisMatchingQueueService.EnqueueResult result = redisMatchingQueueService.enqueueUser(
                    userId, request.categoryId(), queueId);
            if (!result.success()) {
                throw new CustomException(ErrorCode.MATCHING_REDIS_FAILED, result.message());
            }

            int estimatedWaitTime = calculateEstimatedWaitTime(result.position());

            log.debug("매칭 대기열 참가 완료 - userId: {}, queueId: {}, position: {}", userId, queueId, result.position());

            return MatchingResponse.of(
                    queueId,
                    category.getId(),
                    category.getName(),
                    QueueStatus.WAITING,
                    estimatedWaitTime,
                    result.position(),
                    savedQueue.getCreatedAt()
            );
        } catch (CustomException ce) {
            throw ce;
        } catch (Exception e) {
            log.error("매칭 대기열 참가 실패 - userId: {}, categoryId: {}", userId, request.categoryId(), e);
            throw new CustomException(ErrorCode.MATCHING_FAILED);
        }

    }


    @Transactional(readOnly = true)
    public MatchingStatusResponse getMatchingStatus(Long userId) {
        log.debug("매칭 상태 조회 - userId: {}", userId);

        RedisMatchingQueueService.QueueStatusInfo queueStatusInfo = redisMatchingQueueService.getQueueStatus(userId);

        if (queueStatusInfo == null) {
            return MatchingStatusResponse.notInQueue();
        }

        Optional<Category> categoryOptional = categoryRepository.findById(queueStatusInfo.categoryId());
        if (categoryOptional.isEmpty()) {
            return MatchingStatusResponse.notInQueue();
        }

        Category category = categoryOptional.get();

        long waitingCount = queueStatusInfo.totalWaiting();
        int estimateWaitTime = calculateEstimatedWaitTime(queueStatusInfo.position());

        return MatchingStatusResponse.inQueue(
                queueStatusInfo.queueId(),
                category.getId(),
                category.getName(),
                QueueStatus.WAITING,
                estimateWaitTime,
                queueStatusInfo.position(),
                waitingCount
        );

    }


    @Transactional
    public void cancelMatching(Long userId, String queueId) {
        log.debug("매칭 취소 - userId: {}, queueId: {}", userId, queueId);

        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. 소유권 검증
        MatchingQueue queue = matchingQueueRepository.findByQueueId(queueId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUEUE_NOT_FOUND));
        if (!queue.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Long categoryId = RedisMatchingConstants.KeyBuilder.parseCategoryIdFromQueueId(queueId);
        if (categoryId == null) {
            categoryId = queue.getCategory().getId();
        }

        // 2. Redis 탈퇴 시도
        RedisMatchingQueueService.DequeueResult result =
                redisMatchingQueueService.dequeueUser(userId, categoryId);

        // 3. Redis 결과에 따른 처리 정책
        if (!result.success() &&
                !RedisMatchingConstants.ResponseMessage.NOT_IN_QUEUE.equals(result.message()) &&
                !RedisMatchingConstants.ResponseMessage.LOCK_FAILED.equals(result.message())) {
            throw new CustomException(ErrorCode.MATCHING_FAILED, result.message());
        }

        if (RedisMatchingConstants.ResponseMessage.LOCK_FAILED.equals(result.message())) {
            log.warn("Redis 락 이슈로 취소 보류 - userId: {}, categoryId: {}, message: {}",
                    userId, categoryId, result.message());
        }

        // 4. DB 상태 변경 (소유권 검증을 통과한 경우에만)
        try {
            queue.cancel();
            matchingQueueRepository.save(queue);
        } catch (Exception e) {
            log.error("DB 매칭 큐 취소 실패 - queueId: {}", queueId, e);
        }
        log.info("매칭 취소 성공 - userId: {}, queueId: {}", userId, queueId);

    }

    @Transactional(readOnly = true)
    public Map<Long, MatchingCategoryStats> getAllMatchingStats() {
        log.debug("전체 매칭 통계 조회");

        Map<Long, MatchingCategoryStats> statsMap = new LinkedHashMap<>();

        try {
            // Redis에서 실시간 대기 현황 조회
            Map<Long, Long> waitingStats = redisMatchingQueueService.getAllCategoryStats();

            // 활성 카테고리 정보와 결합
            List<Category> activeCategories = categoryRepository.findByIsActiveTrueOrderByName();

            for (Category category : activeCategories) {
                Long waitingCount = waitingStats.getOrDefault(category.getId(), 0L);

                MatchingCategoryStats stats = new MatchingCategoryStats(
                        category.getId(),
                        category.getName(),
                        waitingCount,
                        category.getCategoryType()
                );

                statsMap.put(category.getId(), stats);
            }

            log.debug("매칭 통계 조회 완료 - 카테고리 수: {}", statsMap.size());
            return statsMap;

        } catch (Exception e) {
            log.error("매칭 통계 조회 실패", e);
            return Collections.emptyMap();
        }
    }


    private String generateQueueId(Long userId, Long categoryId) {
        return String.format("queue_%d_%d_%s", userId, categoryId,
                UUID.randomUUID().toString().substring(0, 8));
    }

    private int calculateEstimatedWaitTime(Integer position) {
        if (position == null || position <= 1) {
            return 0;
        }
        int waitTime = (position -1) * MatchingValidationConstants.WaitTime.ESTIMATED_WAIT_TIME_PER_PERSON;
        return Math.min(waitTime, MatchingValidationConstants.WaitTime.MAX_ESTIMATED_WAIT_TIME);
    }
}
