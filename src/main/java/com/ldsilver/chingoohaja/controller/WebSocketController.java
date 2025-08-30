package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.domain.user.CustomUserDetails;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatusResponse;
import com.ldsilver.chingoohaja.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MatchingService matchingService;

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


    /**
     * 하트비트 요청 메시지, 응답 메시지
     */
    public record HeartbeatMessage(long timestamp) {}
    public record HeartbeatResponse(long clientTimestamp, long serverTimestamp) {}

}
