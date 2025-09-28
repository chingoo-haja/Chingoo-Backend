package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.WebSocketEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/debug")
@RequiredArgsConstructor
@Tag(name = "디버깅", description = "WebSocket 연결 상태 확인용 API")
@SecurityRequirement(name = "Bearer Authentication")
public class WebSocketDebugController {
    private final WebSocketEventService webSocketEventService;
    private final SimpUserRegistry userRegistry;

    @Operation(
            summary = "WebSocket 연결 상태 확인",
            description = "현재 사용자의 WebSocket 연결 상태와 전체 연결 현황을 조회합니다."
    )
    @GetMapping("/websocket/status")
    public ApiResponse<Map<String, Object>> getWebSocketStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("WebSocket 상태 확인 요청 - userId: {}", userId);

        Map<String, Object> status = new HashMap<>();

        try {
            // 현재 사용자 연결 상태
            boolean isConnected = userRegistry.getUser(String.valueOf(userId)) != null;
            status.put("currentUserConnected", isConnected);
            status.put("currentUserId", userId);

            // 전체 연결 사용자 수
            int totalConnections = userRegistry.getUsers().size();
            status.put("totalConnections", totalConnections);

            // 연결된 사용자 목록 (디버깅용)
            var connectedUserIds = userRegistry.getUsers().stream()
                    .map(user -> user.getName())
                    .toList();
            status.put("connectedUserIds", connectedUserIds);

            log.info("WebSocket 상태 조회 완료 - userId: {}, connected: {}, total: {}",
                    userId, isConnected, totalConnections);

            return ApiResponse.ok("WebSocket 상태 조회 성공", status);

        } catch (Exception e) {
            log.error("WebSocket 상태 조회 실패 - userId: {}", userId, e);
            status.put("error", e.getMessage());
            return ApiResponse.ok("WebSocket 상태 조회 실패", status);
        }
    }

    @Operation(
            summary = "테스트 알림 전송",
            description = "현재 사용자에게 테스트 WebSocket 알림을 전송합니다."
    )
    @PostMapping("/websocket/test-notification")
    public ApiResponse<String> sendTestNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "테스트 메시지") String message) {

        Long userId = userDetails.getUserId();
        log.info("테스트 알림 전송 요청 - userId: {}, message: {}", userId, message);

        try {
            // 개인 메시지로 테스트 알림 전송
            Map<String, Object> testMessage = Map.of(
                    "type", "TEST",
                    "message", message,
                    "timestamp", System.currentTimeMillis(),
                    "userId", userId
            );

            webSocketEventService.sendPersonalMessage(userId, testMessage);

            log.info("테스트 알림 전송 완료 - userId: {}", userId);
            return ApiResponse.ok("테스트 알림 전송 성공");

        } catch (Exception e) {
            log.error("테스트 알림 전송 실패 - userId: {}", userId, e);
            return ApiResponse.ok("테스트 알림 전송 실패: " + e.getMessage());
        }
    }

    @Operation(
            summary = "가짜 매칭 성공 알림 전송",
            description = "테스트용 매칭 성공 알림을 전송합니다."
    )
    @PostMapping("/websocket/test-matching")
    public ApiResponse<String> sendTestMatchingNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("테스트 매칭 알림 전송 요청 - userId: {}", userId);

        try {
            // 가짜 매칭 성공 데이터
            Long fakeCallId = 999999L;
            Long fakePartnerId = 888888L;
            String fakePartnerNickname = "테스트상대방";

            webSocketEventService.sendMatchingSuccessNotification(
                    userId, fakeCallId, fakePartnerId, fakePartnerNickname);

            log.info("테스트 매칭 알림 전송 완료 - userId: {}", userId);
            return ApiResponse.ok("테스트 매칭 알림 전송 성공");

        } catch (Exception e) {
            log.error("테스트 매칭 알림 전송 실패 - userId: {}", userId, e);
            return ApiResponse.ok("테스트 매칭 알림 전송 실패: " + e.getMessage());
        }
    }
}
