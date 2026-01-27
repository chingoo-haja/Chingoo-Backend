package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.dto.setting.OperatingHoursInfo;
import com.ldsilver.chingoohaja.service.OperatingHoursService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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