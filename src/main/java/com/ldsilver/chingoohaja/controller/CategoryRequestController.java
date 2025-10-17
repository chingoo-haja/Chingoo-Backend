package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.category.request.DesireCategoryRequest;
import com.ldsilver.chingoohaja.dto.category.response.DesireCategoryResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.CategoryRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "카테고리 요청", description = "카테고리 추가 요청 API")
@SecurityRequirement(name = "Bearer Authentication")
public class CategoryRequestController {

    private final CategoryRequestService categoryRequestService;

    @PostMapping("/request")
    @Operation(
            summary = "카테고리 추가 요청",
            description = "사용자가 새로운 카테고리 추가를 요청합니다. " +
                    "관리자가 확인하여 승인/거절할 수 있습니다."
    )
    public ApiResponse<DesireCategoryResponse> requestCategory(
            @Valid @RequestBody DesireCategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("카테고리 요청 - userId: {}, categoryName: {}",
                userDetails.getUserId(), request.categoryName());

        DesireCategoryResponse response = categoryRequestService.requestCategory(
                userDetails.getUserId(), request);

        return ApiResponse.ok("카테고리 요청이 접수되었습니다. 검토 후 추가될 예정입니다.", response);
    }
}