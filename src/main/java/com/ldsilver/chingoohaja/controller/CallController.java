package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.call.request.TokenRequest;
import com.ldsilver.chingoohaja.dto.call.response.CallStatusResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.AgoraTokenService;
import com.ldsilver.chingoohaja.service.CallStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/token/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @Valid @RequestBody TokenRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        TokenResponse response = agoraTokenService.generateRtcToken(request);
        return ApiResponse.ok("토큰 갱신 성공", response);
    }

}
