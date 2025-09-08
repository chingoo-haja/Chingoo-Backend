package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingStatsRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatsResponse;
import com.ldsilver.chingoohaja.dto.matching.response.RealtimeMatchingStatsResponse;
import com.ldsilver.chingoohaja.service.MatchingStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/matching/stats")
@RequiredArgsConstructor
@Tag(name = "매칭 통계", description = "실시간 매칭 대기열 및 통계 조회 API")
@SecurityRequirement(name = "Bearer Authentication")
public class MatchingStatsController {

    private final MatchingStatsService matchingStatsService;

    @Operation(
            summary = "실시간 매칭 통계 조회",
            description = "현재 카테고리별 대기 인원, 예상 대기시간, 매칭률 등의 실시간 통계를 조회합니다. " +
                    "매칭 대기열에 참가하기 전 참고 정보로 활용할 수 있습니다."
    )
    @GetMapping("/realtime")
    public ApiResponse<RealtimeMatchingStatsResponse> getRealtimeStats() {
        log.debug("실시간 매칭 통계 조회 요청");

        RealtimeMatchingStatsResponse stats = matchingStatsService.getRealtimeMatchingStats();
        return ApiResponse.ok("실시간 매칭 통계 조회 성공", stats);
    }

    @Operation(
            summary = "카테고리별 상세 매칭 통계",
            description = "특정 카테고리의 상세한 매칭 통계를 조회합니다. " +
                    "시간대별 매칭 성공률, 평균 대기시간 등의 분석 데이터를 제공합니다."
    )
    @GetMapping("/category/{categoryId}")
    public ApiResponse<MatchingStatsResponse> getCategoryStats(
            @Parameter(description = "카테고리 ID", example = "1")
            @PathVariable Long categoryId,
            @Valid MatchingStatsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            log.warn("인증되지 않은 사용자의 카테고리 통계 요청 - categoryId: {}", categoryId);
            throw new CustomException(ErrorCode.UNAUTHORIZED, "로그인이 필요한 서비스입니다.");
        }

        log.debug("카테고리별 매칭 통계 조회 - categoryId: {}, userId: {}", categoryId, userDetails.getUserId());

        MatchingStatsResponse stats = matchingStatsService.getCategoryMatchingStats(
                categoryId, request, userDetails.getUserId());

        return ApiResponse.ok("카테고리별 매칭 통계 조회 성공", stats);
    }
}
