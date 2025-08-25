package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingResponse;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MatchingService {
    private final RedisMatchingQueueService redisMatchingQueueService;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public MatchingResponse joinMatchingQueue(Long userId, MatchingRequest request) {
        log.debug("매칭 대기열 참가 시작 - userId: {}, categoryId: {}", userId, request.categoryId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.isActive()) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_ACTIVE);
        }

        String queueId = redisMatchingQueueService.joinQueue(
                userId,
                category.getId(),
                category.getName()
        );

        int estimateWaitTime = redisMatchingQueueService.estimateWaitTime(userId, category.getId());
        int queuePosition = redisMatchingQueueService.getQueuePosition(queueId, category.getId());

        log.debug("매칭 대기열 참가 완료 - userId: {}, queueId: {}, position: {}", userId, queueId, queuePosition);

        return MatchingResponse.waiting(
                queueId,
                category.getId(),
                category.getName(),
                estimateWaitTime,
                queuePosition
        );
    }
}
