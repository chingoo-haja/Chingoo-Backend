package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.call.response.CallStatusResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.CallStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "통화", description = "음성 통화 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class CallController {

    private final CallStatusService callStatusService;

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

}
