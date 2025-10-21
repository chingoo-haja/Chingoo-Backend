// src/main/java/com/ldsilver/chingoohaja/service/CategoryRequestService.java
package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.category.DesiredCategory;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.category.request.DesireCategoryRequest;
import com.ldsilver.chingoohaja.dto.category.response.DesireCategoryResponse;
import com.ldsilver.chingoohaja.repository.CategoryRequestRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRequestService {

    private final CategoryRequestRepository categoryRequestRepository;
    private final UserRepository userRepository;

    private static final int DUPLICATE_CHECK_DAYS = 7; // 중복 체크 기간 (7일)

    @Transactional
    public DesireCategoryResponse requestCategory(Long userId, DesireCategoryRequest dto) {
        log.debug("카테고리 요청 - userId: {}, categoryName: {}", userId, dto.categoryName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 중복 요청 체크 (최근 7일 이내 동일한 카테고리 요청이 있는지)
        LocalDateTime since = LocalDateTime.now().minusDays(DUPLICATE_CHECK_DAYS);
        boolean isDuplicate = categoryRequestRepository.existsByUserAndCategoryNameAndCreatedAtAfter(
                user, dto.categoryName(), since);

        if (isDuplicate) {
            log.warn("중복 카테고리 요청 - userId: {}, categoryName: {}", userId, dto.categoryName());
            throw new CustomException(ErrorCode.DUPLICATE_REQUEST,
                    "이미 최근에 같은 카테고리를 요청하셨습니다.");
        }

        // 카테고리 요청 저장
        DesiredCategory categoryRequest = DesiredCategory.of(user, dto.categoryName());
        DesiredCategory savedRequest = categoryRequestRepository.save(categoryRequest);

        log.info("카테고리 요청 저장 완료 - requestId: {}, userId: {}, categoryName: {}",
                savedRequest.getId(), userId, dto.categoryName());

        return DesireCategoryResponse.from(savedRequest);
    }
}