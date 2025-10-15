package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.call.request.CallStatisticsRequest;
import com.ldsilver.chingoohaja.dto.call.response.CallStatusResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenRenewResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.AgoraTokenService;
import com.ldsilver.chingoohaja.service.CallStatisticsService;
import com.ldsilver.chingoohaja.service.CallStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "통화", description = "음성 통화 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class CallController {

    private final CallStatusService callStatusService;
    private final AgoraTokenService agoraTokenService;
    private final CallStatisticsService callStatisticsService;

    @Operation(
            summary = "통화 상태 조회",
            description = "현재 진행 중인 통화의 상태를 조회힙니다. " +
                    "통화 참가자만 조회할 수 있습니다."
    )
    @GetMapping("/{callId}/status")
    public ApiResponse<CallStatusResponse> getCallStatus(
            @Parameter(description = "통화 ID", example = "1")
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("통화 상태 조회 요청 - callId: {}, userId: {}", callId, userDetails.getUserId());

        CallStatusResponse response = callStatusService.getCallStatus(callId, userDetails.getUserId());
        return ApiResponse.ok("통화 상태 조회 성공", response);
    }

    @Operation(
            summary = "통화 종료",
            description = "현재 진행 중인 통화를 종료합니다. " +
                    "통화 참가자만 종료할 수 있으며, 자동으로 녹음도 중지됩니다."
    )
    @PostMapping("/{callId}/end")
    public ApiResponse<CallStatusResponse> endCall(
            @Parameter(description = "통화 ID", example = "1")
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            log.error("통화 종료 실패: 인증되지 않은 요청 - callId: {}", callId);
            throw new CustomException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        log.debug("통화 종료 요청 - callId: {}, userId: {}", callId, userDetails.getUserId());

        CallStatusResponse response = callStatusService.endCall(callId, userDetails.getUserId());
        return ApiResponse.ok("통화 종료 성공", response);
    }

    @Operation(
            summary = "내 활성 통화 조회",
            description = "현재 사용자가 참여 중인 활성 통화를 조회합니다."
    )
    @GetMapping("/active")
    public ApiResponse<CallStatusResponse> getMyActiveCall(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.debug("활성 통화 조회 요청 - userId: {}", userDetails.getUserId());

        CallStatusResponse response = callStatusService.getActiveCallByUserId(userDetails.getUserId());
        return ApiResponse.ok("활성 통화 조회 성공", response);
    }

    @PostMapping("/{callId}/renew-token")
    @Operation(
            summary = "RTC Token 갱신",
            description = "통화 중 RTC Token이 만료되기 전에 새로운 토큰을 발급받습니다. " +
                    "Agora SDK의 'token-privilege-will-expire' 이벤트 발생 시 호출해야 합니다."
    )
    public ResponseEntity<ApiResponse<TokenRenewResponse>> renewToken(
            @Parameter(description = "통화 ID", required = true)
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.debug("RTC Token 갱신 요청 - userId: {}, callId: {}",
                userDetails.getUserId(), callId);

        TokenRenewResponse response = agoraTokenService.renewRtcToken(
                userDetails.getUserId(),
                callId
        );

        return ResponseEntity.ok(ApiResponse.ok("RTC Token 갱신 성공",response));
    }

    /**
     * 통화 통계 저장
     * - 프론트엔드에서 Agora 통화 종료 후 통계 정보를 전송
     * - 통화 품질, 데이터 사용량 등 분석을 위한 데이터 수집
     */
    @PostMapping("/{callId}/statistics")
    @Operation(
            summary = "통화 통계 저장",
            description = "통화 종료 후 Agora SDK에서 수집한 통계 정보를 저장합니다. " +
                    "통화 품질 분석, 데이터 사용량 추적 등에 활용됩니다."
    )
    public ApiResponse<Void> saveCallStatistics(
            @Parameter(description = "통화 ID", required = true)
            @PathVariable Long callId,

            @Parameter(description = "통화 통계 정보", required = true)
            @Valid @RequestBody CallStatisticsRequest request,

            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.debug("통화 통계 저장 요청 - userId: {}, callId: {}, duration: {}초",
                userDetails.getUserId(), callId, request.duration());

        callStatisticsService.saveCallStatistics(
                userDetails.getUserId(),
                callId,
                request
        );

        return ApiResponse.ok();
    }
}
