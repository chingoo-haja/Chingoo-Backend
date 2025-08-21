package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.catetory.response.CategoryResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "카테고리", description = "통화 카테고리 조회 API")
public class CategoryController {
    private final CategoryService categoryService;

    @Operation(
            summary = "카테고리 목록 조회",
            description = "사용 가능한 카테고리 목록을 조회합니다." +
                    "is_active 파라미터로 활성/비활성 카테고리를 필터링할 수 있습니다."
    )
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(
            @Parameter(description = "활성 상태 필터 (true: 활성만, false: 비활성만, null: 전체)", example = "true")
            @RequestParam(value = "is_active", required = false) Boolean isActive) {
        log.debug("카테고리 목록 조회 요청 - isActive: {}", isActive);

        List<CategoryResponse> categories = categoryService.getAllCategories(isActive);

        String message = isActive == null ? "전체 카테고리 조회 성공" :
                isActive ? "활성 카테고리 조회 성공" : "비활성 카테고리 조회 성공";

        return ApiResponse.ok(message, categories);
    }


    @Operation(
            summary = "활성 카테고리 목록 조회",
            description = "현재 사용 가능한 활성 카테고리 목록만 조회합니다. " +
                    "매칭 시 사용할 카테고리 목록을 가져올 때 사용합니다."
    )
    @GetMapping("/active")
    public ApiResponse<List<CategoryResponse>> getActiveCategories() {
        log.debug("활성 카테고리 목록 조회 요청");

        List<CategoryResponse> activeCategories = categoryService.getActiveCategories();
        return ApiResponse.ok("활성 카테고리 조회 성공", activeCategories);
    }
}
