package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingConstants;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingResponse;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
            MatchingQueue matchingQueue = MatchingQueue.from(user, category, QueueType.RANDOM_MATCH);
            MatchingQueue savedQueue = matchingQueueRepository.save(matchingQueue);

            // Redis 대기열에 참가
            RedisMatchingQueueService.EnqueueResult result = redisMatchingQueueService.enqueueUser(
                    userId, request.categoryId(), queueId);
            if (!result.success()) {
                throw new CustomException(ErrorCode.MATCHING_REDIS_FAILED, result.message());
            }

            int estimatedWaitTime = calculateEstimatedWaitTime(result.position());

            log.debug("매칭 대기열 참가 완료 - userId: {}, queueId: {}, position: {}", userId, request.categoryId(), result.position());

            return MatchingResponse.of(
                    queueId,
                    category.getId(),
                    category.getName(),
                    QueueStatus.WAITING,
                    estimatedWaitTime,
                    result.position(),
                    savedQueue.getCreatedAt()
            );
        } catch (Exception e) {
            log.error("매칭 대기열 참가 실패 - userId: {}, categoryId: {}", userId, request.categoryId(), e);
            throw new CustomException(ErrorCode.MATCHING_FAILED);
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
        int waitTime = (position -1) * MatchingConstants.WaitTime.ESTIMATED_WAIT_TIME_PER_PERSON;
        return Math.min(waitTime, MatchingConstants.WaitTime.MAX_ESTIMATED_WAIT_TIME);
    }
}
