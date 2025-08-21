package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.dto.category.response.CategoryResponse;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories(Boolean isActive) {
        log.debug("카테고리 목록 조회 시작 - isActive: {}", isActive);

        List<Category> categories = (isActive == null)
                ? categoryRepository.findAllByOrderByName()
                : categoryRepository.findByIsActiveOrderByName(isActive);

        List<CategoryResponse> responses = categories.stream()
                .map(CategoryResponse::from)
                .toList();

        log.debug("카테고리 목록 조회 완료 - 조회된 카테고리 수: {}", responses.size());
        return responses;
    }

    public List<CategoryResponse> getActiveCategories() {
        log.debug("활성 카테고리 목록 조회 시작");

        List<Category> activeCategories = categoryRepository.findByIsActiveTrueOrderByName();

        List<CategoryResponse> responses = activeCategories.stream()
                .map(CategoryResponse::from)
                .toList();

        log.debug("활성 카테고리 목록 조회 완료 - 조회된 카테고리 수: {}", responses.size());
        return responses;
    }
}
