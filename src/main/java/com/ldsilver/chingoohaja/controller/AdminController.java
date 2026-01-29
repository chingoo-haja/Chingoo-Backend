package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingStatsRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingQueueCleanupResponse;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingQueueHealthResponse;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatsResponse;
import com.ldsilver.chingoohaja.dto.matching.response.RealtimeMatchingStatsResponse;
import com.ldsilver.chingoohaja.dto.setting.OperatingHoursInfo;
import com.ldsilver.chingoohaja.service.AdminMatchingService;
import com.ldsilver.chingoohaja.service.MatchingStatsService;
import com.ldsilver.chingoohaja.service.OperatingHoursService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "관리자", description = "관리자 전용 API")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final OperatingHoursService operatingHoursService;
    private final MatchingStatsService matchingStatsService;
    private final AdminMatchingService adminMatchingService;

    @Operation(
            summary = "운영 시간 변경",
            description = "통화 서비스의 운영 시간을 변경합니다. (관리자 전용)"
    )
    @PutMapping("/operating-hours")
    public ApiResponse<String> updateOperatingHours(
            @RequestParam(name = "start_time") String startTime,
            @RequestParam(name = "end_time") String endTime,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        validateTimeFormat(startTime, endTime);
        operatingHoursService.updateOperatingHours(startTime, endTime);

        log.info("운영 시간 변경 완료 - {} ~ {}", startTime, endTime);
        return ApiResponse.ok(
                "운영 시간 변경 성공",
                String.format("운영 시간이 %s ~ %s 로 변경되었습니다.", startTime, endTime)
        );
    }


    @Operation(
            summary = "서비스 활성화/비활성화",
            description = "통화 서비스를 활성화하거나 비활성화합니다. (관리자 전용)"
    )
    @PutMapping("/service-toggle")
    public ApiResponse<String> toggleService(
            @RequestParam boolean enabled,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        operatingHoursService.toggleService(enabled);

        log.info("서비스 상태 변경 - enabled: {}", enabled);
        return ApiResponse.ok(
                "서비스 상태 변경 성공",
                String.format("통화 서비스가 %s 되었습니다.", enabled ? "활성화" : "비활성화")
        );
    }


    @Operation(
            summary = "현재 운영 시간 조회",
            description = "현재 운영 시간 설정을 조회합니다. (관리자 전용)"
    )
    @GetMapping("/operating-hours")
    public ApiResponse<OperatingHoursInfo> getOperatingHours(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        OperatingHoursInfo info = operatingHoursService.getOperatingHoursInfo();
        return ApiResponse.ok("운영 설정 조회 성공", info);
    }


    @Operation(
            summary = "실시간 매칭 통계 조회",
            description = "현재 카테고리별 대기 인원, 예상 대기시간, 매칭률 등의 실시간 통계를 조회합니다. " +
                    "매칭 대기열에 참가하기 전 참고 정보로 활용할 수 있습니다."
    )
    @GetMapping("/matching/realtime")
    public ApiResponse<RealtimeMatchingStatsResponse> getRealtimeStats(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.debug("관리자 실시간 매칭 통계 조회 - adminId: {}", userDetails.getUserId());

        RealtimeMatchingStatsResponse stats = matchingStatsService.getRealtimeMatchingStats();
        return ApiResponse.ok("실시간 매칭 통계 조회 성공", stats);
    }


    @Operation(
            summary = "카테고리별 상세 매칭 통계",
            description = "특정 카테고리의 상세한 매칭 통계를 조회합니다. " +
                    "시간대별 매칭 성공률, 평균 대기시간 등의 분석 데이터를 제공합니다."
    )
    @GetMapping("/matching/category/{categoryId}")
    public ApiResponse<MatchingStatsResponse> getCategoryStats(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "DAILY") String period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") Boolean includeTrends,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("관리자 카테고리 매칭 통계 조회 - adminId: {}, categoryId: {}, period: {}",
                userDetails.getUserId(), categoryId, period);

        MatchingStatsRequest request = new MatchingStatsRequest(
                period, startDate, endDate,
                categoryId,
                10, 0,
                "RECENT", "DESC",
                false, includeTrends,
                "Asia/Seoul"
        );

        MatchingStatsResponse stats = matchingStatsService.getCategoryMatchingStats(
                categoryId, request, userDetails.getUserId()
        );

        return ApiResponse.ok("카테고리별 매칭 통계 조회 성공", stats);
    }

    @Operation(
            summary = "매칭 큐 긴급 정리",
            description = "특정 카테고리의 매칭 대기열을 강제로 정리합니다." +
                    "Redis 대기열 전체 삭제 후 DB WAITING 상태를 EXPIRED로 일괄 변경합니다.")
    @PostMapping("/matching/cleanup/{categoryId}")
    public ApiResponse<MatchingQueueCleanupResponse> cleanupMatchingQueue(
            @PathVariable Long categoryId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.warn("⚠️ 관리자 매칭 큐 긴급 정리 - adminId: {}, categoryId: {}",
                userDetails.getUserId(), categoryId);

        MatchingQueueCleanupResponse result = adminMatchingService.cleanupMatchingQueue(categoryId);
        return ApiResponse.ok("매칭 큐 정리 완료", result);
    }


    @Operation(
            summary = "매칭 큐 헬스 체크",
            description = "특정 카테고리의 매칭 대기열 현재 상태를 실시간으로 점검합니다."
    )
    @GetMapping("/matching/health/{categoryId}")
    public ApiResponse<MatchingQueueHealthResponse> checkMatchingHealth(
            @Parameter(description = "점검할 카테고리 ID", example = "4")
            @PathVariable Long categoryId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("매칭 큐 헬스 체크 - adminId: {}, categoryId: {}",
                userDetails.getUserId(), categoryId);

        MatchingQueueHealthResponse response = adminMatchingService.checkMatchingHealth(categoryId);
        return ApiResponse.ok("매칭 큐 헬스 체크 완료", response);
    }


    private void validateTimeFormat(String startTime, String endTime) {
        try {
            LocalTime.parse(startTime);
            LocalTime.parse(endTime);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "시간 형식이 올바르지 않습니다. (HH:mm 형식을 사용하세요)");
        }
    }

}