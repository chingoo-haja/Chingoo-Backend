package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatusResponse;
import com.ldsilver.chingoohaja.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MatchingService matchingService;
    private final SimpMessagingTemplate messagingTemplate;
    /**
     * 매칭 상태 구독 요청
     */
    @MessageMapping("/matching/subscribe")
    @SendToUser("/queue/matching")
    public MatchingStatusResponse subscribeMatchingStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("매칭 상태 구독 요청 - userId: {}", userDetails.getUserId());

        try {
            return matchingService.getMatchingStatus(userDetails.getUserId());
        } catch (Exception e) {
            log.error("매칭 상태 구독 처리 실패 - userId: {}", userDetails.getUserId(), e);
            return MatchingStatusResponse.notInQueue();
        }
    }

    /**
     * 통화 종료 알림
     * 클라이언트: /app/call-end/{partnerId}
     * 상대방에게 전송: /user/queue/call-end
     */
    @MessageMapping("/call-end/{partnerId}")
    public void handleCallEnd(
            @DestinationVariable Long partnerId,
            @Payload CallEndMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Long userId = extractUserIdFromPrincipal(headerAccessor);

        if (userId == null) {
            log.error("[통화종료] 인증되지 않은 요청");
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[통화종료] WebSocket 수신 - from: {}, to: {}, callId: {}, reason: {}",
                userId, partnerId, message.callId(), message.reason());


        try {
            // 상대방에게 통화 종료 알림 전송
            CallEndNotification notification = new CallEndNotification(
                    message.callId(),
                    userId,
                    message.reason(),
                    System.currentTimeMillis()
            );

            messagingTemplate.convertAndSendToUser(
                    partnerId.toString(),
                    "/queue/call-end",
                    notification
            );

            log.info("✅ [통화종료] WebSocket 전송 완료 - to: {}, callId: {}",
                    partnerId, message.callId());

        } catch (Exception e) {
            log.error("❌ [통화종료] 전송 실패 - to: {}, callId: {}",
                    partnerId, message.callId(), e);
            throw new CustomException(ErrorCode.WEBSOCKET_SEND_FAILED);
        }
    }


    /**
     * 하트비트 처리 (연결 상태 확인)
     */
    @MessageMapping("/heartbeat")
    @SendToUser("/queue/heartbeat")
    public HeartbeatResponse handleHeartbeat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Payload HeartbeatMessage message)
    {
        Long userId = (userDetails != null ? userDetails.getUserId() : null);
        log.debug("하트비트 수신 - userId: {}, timestamp: {}", userId, message.timestamp());

        // 서버 타임스탬프와 함께 ACK 응답
        return new HeartbeatResponse(message.timestamp(), System.currentTimeMillis());
    }

    /**
     * 클라이언트 연결 해제 알림
     */
    @MessageMapping("/disconnect")
    public void handleDisconnect(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("클라이언트 연결 해제 알림 - userId: {}", userDetails.getUserId());

        try {
            // 대기 중인 매칭이 있으면 취소 처리
            MatchingStatusResponse status = matchingService.getMatchingStatus(userDetails.getUserId());
            if (status.isInQueue()) {
                matchingService.cancelMatching(userDetails.getUserId(), status.queueId());
                log.info("연결 해제로 인한 매칭 자동 취소 - userId: {}", userDetails.getUserId());
            }
        } catch (Exception e) {
            log.error("연결 해제 처리 실패 - userId: {}", userDetails.getUserId(), e);
        }
    }

    private Long extractUserIdFromPrincipal(SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();

        if (principal == null) {
            log.error("Principal이 null입니다");
            return null;
        }

        log.debug("Principal 타입: {}", principal.getClass().getName());

        // Principal이 Authentication 타입인지 확인
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            Object principalObj = auth.getPrincipal();

            if (principalObj == null) {
                log.error("Authentication.principal이 null입니다");
                return null;
            }

            log.debug("Authentication.principal 타입: {}", principalObj.getClass().getName());

            if (principalObj instanceof CustomUserDetails) {
                CustomUserDetails userDetails = (CustomUserDetails) principalObj;

                // user 필드가 null인 경우 username(=userId)에서 추출
                if (userDetails.getUser() == null) {
                    log.warn("CustomUserDetails의 user가 null - username으로 userId 추출");
                    try {
                        return Long.parseLong(userDetails.getUsername());
                    } catch (NumberFormatException e) {
                        log.error("username을 Long으로 변환 실패: {}", userDetails.getUsername());
                        return null;
                    }
                } else {
                    return userDetails.getUserId();
                }
            } else {
                log.error("Principal이 CustomUserDetails가 아님: {}", principalObj.getClass());
                return null;
            }
        } else {
            // Principal.getName()이 userId를 반환하는 경우
            log.warn("Principal이 Authentication이 아님 - getName()으로 userId 추출");
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                log.error("Principal.getName()을 Long으로 변환 실패: {}", principal.getName());
                return null;
            }
        }
    }


    /**
     * 하트비트 요청 메시지, 응답 메시지
     */
    public record HeartbeatMessage(long timestamp) {}
    public record HeartbeatResponse(long clientTimestamp, long serverTimestamp) {}

    /**
     * 통화 종료 메시지 (클라이언트 → 서버)
     */
    public record CallEndMessage(
            Long callId,
            String reason // "USER_LEFT", "REFRESH", "NETWORK_ERROR" 등
    ) {}

    /**
     * 통화 종료 알림 (서버 → 상대방)
     */
    public record CallEndNotification(
            Long callId,
            Long userId, // 종료를 요청한 사용자
            String reason,
            Long timestamp
    ) {}
}
