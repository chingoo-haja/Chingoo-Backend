package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.dto.matching.response.MatchingNotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketEventService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMatchingSuccessNotification(Long userId, Long callId, Long partnerId, String partnerNickname) {
        try {
            MatchingNotificationResponse response = MatchingNotificationResponse.success(
                    callId, partnerId, partnerNickname
            );

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/matching",
                    response
            );

            log.debug("매칭 성공 알림 전송 완료 - userId: {}, callId: {}", userId, callId);
        } catch (Exception e) {
            log.error("매칭 성공 알림 전송 실패 - userId: {}, callId: {}", userId, callId, e);
        }
    }

    public void sendMatchingCancelledNotification(Long userId, String reason) {
        try {
            MatchingNotificationResponse response = MatchingNotificationResponse.cancelled(reason);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/matching",
                    response
            );

            log.debug("매칭 취소 알림 전송 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("매칭 취소 알림 전송 실패 - userId: {}", userId, e);
        }
    }

    public void sendQueueStatusUpdate(Long userId, Integer position, Integer estimateWaitTime) {
        try {
            MatchingNotificationResponse response = MatchingNotificationResponse.queueUpdate(
                    position, estimateWaitTime
            );

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/matching",
                    response
            );

            log.debug("대기열 상태 전송 완료 - userId: {}, position: {}", userId, position);
        } catch (Exception e) {
            log.error("대기열 상태 전송 실패 - userId: {}", userId, e);
        }
    }

    public void sendCallStatusUpdate(Long callId, String status, Long... userIds) {
        try {
            for (Long userId : userIds) {
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(userId),
                        "/queue/calls",
                        new CallStatusUpdateMessage(callId, status)
                );
            }

            log.debug("통화 상태 변경 알림 전송 완료 - callId: {}, status: {}", callId, status);
        } catch (Exception e) {
            log.error("통화 상태 변경 알림 전송 실패 - callId: {}", callId, e);
        }
    }

    public void sendPersonalMessage(Long userId, Object message) {
        try {
            String destination = "/queue/user/" + userId;
            messagingTemplate.convertAndSend(destination, message);

            log.debug("개인 메시지 전송 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("개인 메시지 전송 실패 - userId: {}", userId, e);
        }
    }

    public record CallStatusUpdateMessage(
            Long callId,
            String status
    ) {}
}
