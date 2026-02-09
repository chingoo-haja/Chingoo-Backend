package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.admin.response.*;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "관리자 대시보드", description = "관리자 전용 대시보드 API")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(
            summary = "대시보드 개요 조회",
            description = "시스템 상태, 실시간 통계, 오늘의 요약 정보를 조회합니다."
    )
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> getDashboardOverview() {
        log.debug("관리자 대시보드 개요 조회 요청");

        DashboardOverviewResponse overview = adminDashboardService.getDashboardOverview();
        return ApiResponse.ok("대시보드 개요 조회 성공", overview);
    }

    @Operation(
            summary = "사용자 목록 조회",
            description = "전체 사용자 목록을 페이징하여 조회합니다. 검색 및 필터링 가능."
    )
    @GetMapping("/users")
    public ApiResponse<AdminUserListResponse> getUsers(
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(defaultValue = "20") int limit,

            @Parameter(description = "검색어 (이메일, 닉네임)")
            @RequestParam(required = false) String search,

            @Parameter(description = "사용자 타입 필터 (USER, GUARDIAN, ADMIN)")
            @RequestParam(required = false) String userType,

            @Parameter(description = "정렬 기준 (created_at, last_login, report_count)")
            @RequestParam(defaultValue = "created_at") String sortBy,

            @Parameter(description = "정렬 순서 (asc, desc)")
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        log.debug("사용자 목록 조회 - page: {}, search: {}", page, search);

        AdminUserListResponse users = adminDashboardService.getUsers(
                page, limit, search, userType, sortBy, sortOrder
        );
        return ApiResponse.ok("사용자 목록 조회 성공", users);
    }

    @Operation(
            summary = "신고 목록 조회",
            description = "전체 신고 내역을 조회합니다. 상태별 필터링 가능."
    )
    @GetMapping("/reports")
    public ApiResponse<ReportListResponse> getReports(
            @Parameter(description = "페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(defaultValue = "20") int limit,

            @Parameter(description = "상태 필터 (PENDING, REVIEWED, RESOLVED)")
            @RequestParam(required = false) String status
    ) {
        log.debug("신고 목록 조회 - page: {}, status: {}", page, status);

        ReportListResponse reports = adminDashboardService.getReports(page, limit, status);
        return ApiResponse.ok("신고 목록 조회 성공", reports);
    }

    @Operation(
            summary = "통화 모니터링",
            description = "현재 진행 중인 통화와 최근 종료된 통화를 모니터링합니다."
    )
    @GetMapping("/calls/monitor")
    public ApiResponse<CallMonitoringResponse> monitorCalls() {
        log.debug("통화 모니터링 요청");

        CallMonitoringResponse monitoring = adminDashboardService.monitorCalls();
        return ApiResponse.ok("통화 모니터링 조회 성공", monitoring);
    }

    @Operation(
            summary = "사용자 매칭 이력 조회",
            description = "특정 사용자의 매칭 큐 이력과 통화 이력을 조회합니다. " +
                    "매칭 요약(시도/성공/취소/만료), 매칭 큐 전체 이력, 통화 이력(페이징)을 포함합니다."
    )
    @GetMapping("/users/{userId}/matching-history")
    public ApiResponse<UserMatchingHistoryResponse> getUserMatchingHistory(
            @Parameter(description = "조회할 사용자 ID", example = "1")
            @PathVariable Long userId,

            @Parameter(description = "통화 이력 페이지 번호", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "통화 이력 페이지당 항목 수", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.debug("사용자 매칭 이력 조회 - userId: {}, page: {}", userId, page);

        UserMatchingHistoryResponse history = adminDashboardService.getUserMatchingHistory(userId, page, limit);
        return ApiResponse.ok("사용자 매칭 이력 조회 성공", history);
    }
}