package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.call.response.ChannelJoinResponse;
import com.ldsilver.chingoohaja.dto.call.response.ChannelResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.service.AgoraTokenService;
import com.ldsilver.chingoohaja.service.CallChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/calls/{callId}/channel")
@RequiredArgsConstructor
@Tag(name = "통화 채널", description = "Agora 채널 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
public class CallChannelController {

    private final CallChannelService callChannelService;
    private final CallRepository callRepository;
    private final AgoraTokenService agoraTokenService;

    @Operation(
            summary = "채널 참가",
            description = "통화 채널에 참가합니다. 토큰 생성과 함께 채널 정보를 제공합니다."
    )
    @PostMapping("/join")
    public ApiResponse<ChannelJoinResponse> joinChannel(
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 1. Call 조회 및 권한 확인
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userDetails.getUserId())) {
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        // 2. 채널 참가
        String channelName = call.getAgoraChannelName();
        ChannelResponse channelResponse = callChannelService.joinChannel(channelName, userDetails.getUserId());

        // 3. 토큰 생성
        TokenResponse tokenResponse = agoraTokenService.generateTokenForCall(
                userDetails.getUserId(), callId);

        ChannelJoinResponse response = new ChannelJoinResponse(
                channelResponse,
                tokenResponse,
                call.getPartner(userDetails.getUserId()).getNickname()
        );

        return ApiResponse.ok("채널 참가 성공", response);
    }

    @Operation(
            summary = "채널 나가기",
            description = "통화 채널에서 나갑니다."
    )
    @PostMapping("/leave")
    public ApiResponse<ChannelResponse> leaveChannel(
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        String channelName = call.getAgoraChannelName();
        ChannelResponse response = callChannelService.leaveChannel(channelName, userDetails.getUserId());

        return ApiResponse.ok("채널 나가기 성공", response);
    }

    @Operation(
            summary = "채널 상태 조회",
            description = "현재 채널의 참가자 정보를 조회합니다."
    )
    @GetMapping("/status")
    public ApiResponse<ChannelResponse> getChannelStatus(
            @PathVariable Long callId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        String channelName = call.getAgoraChannelName();
        ChannelResponse response = callChannelService.getChannelStatus(channelName);

        return ApiResponse.ok("채널 상태 조회 성공", response);
    }
}
