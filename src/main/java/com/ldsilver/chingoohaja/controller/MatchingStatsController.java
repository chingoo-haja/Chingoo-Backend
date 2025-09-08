package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.matching.response.RealtimeMatchingStatsResponse;
import com.ldsilver.chingoohaja.service.MatchingStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
}
