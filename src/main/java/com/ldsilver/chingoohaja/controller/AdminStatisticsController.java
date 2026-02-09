package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.admin.response.AdminStatisticsResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.AdminStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/statistics")
@RequiredArgsConstructor
@Tag(name = "관리자 전체 통계", description = "전체 시스템 누적 통계 API")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;

    @Operation(
            summary = "전체 시스템 통계 요약",
            description = "누적 사용자 수, 통화 수, 매칭 성공률, 평가 통계 등 시스템 전체 요약 정보를 조회합니다."
    )
    @GetMapping("/overview")
    public ApiResponse<AdminStatisticsResponse.OverviewStats> getOverviewStatistics() {
        log.debug("전체 시스템 통계 요약 조회 요청");

        AdminStatisticsResponse.OverviewStats stats = adminStatisticsService.getOverviewStatistics();
        return ApiResponse.ok("전체 시스템 통계 조회 성공", stats);
    }

    @Operation(
            summary = "사용자 통계",
            description = "프로바이더별 분포, 월별 가입 추이, UserType별 분포, 활성 사용자 수 등을 조회합니다."
    )
    @GetMapping("/users")
    public ApiResponse<AdminStatisticsResponse.UserStats> getUserStatistics() {
        log.debug("사용자 통계 조회 요청");

        AdminStatisticsResponse.UserStats stats = adminStatisticsService.getUserStatistics();
        return ApiResponse.ok("사용자 통계 조회 성공", stats);
    }

    @Operation(
            summary = "통화 통계",
            description = "기간별 통화 수, 카테고리별 통화 통계, 일별 추이, 시간대별 분포 등을 조회합니다."
    )
    @GetMapping("/calls")
    public ApiResponse<AdminStatisticsResponse.CallStats> getCallStatistics(
            @Parameter(description = "조회 시작일 (기본: 30일 전)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "조회 종료일 (기본: 오늘)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        log.debug("통화 통계 조회 요청 - 기간: {} ~ {}", start, end);

        AdminStatisticsResponse.CallStats stats = adminStatisticsService.getCallStatistics(start, end);
        return ApiResponse.ok("통화 통계 조회 성공", stats);
    }

    @Operation(
            summary = "매칭 통계",
            description = "기간별 매칭 성공률, 일별/시간대별 매칭 추이를 조회합니다."
    )
    @GetMapping("/matching")
    public ApiResponse<AdminStatisticsResponse.MatchingStats> getMatchingStatistics(
            @Parameter(description = "조회 시작일 (기본: 30일 전)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "조회 종료일 (기본: 오늘)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        log.debug("매칭 통계 조회 요청 - 기간: {} ~ {}", start, end);

        AdminStatisticsResponse.MatchingStats stats = adminStatisticsService.getMatchingStatistics(start, end);
        return ApiResponse.ok("매칭 통계 조회 성공", stats);
    }

    @Operation(
            summary = "평가 통계",
            description = "기간별 평가 수, 피드백 타입별 분포(긍정/중립/부정) 등을 조회합니다."
    )
    @GetMapping("/evaluations")
    public ApiResponse<AdminStatisticsResponse.EvaluationStats> getEvaluationStatistics(
            @Parameter(description = "조회 시작일 (기본: 30일 전)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "조회 종료일 (기본: 오늘)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        log.debug("평가 통계 조회 요청 - 기간: {} ~ {}", start, end);

        AdminStatisticsResponse.EvaluationStats stats = adminStatisticsService.getEvaluationStatistics(start, end);
        return ApiResponse.ok("평가 통계 조회 성공", stats);
    }
}
