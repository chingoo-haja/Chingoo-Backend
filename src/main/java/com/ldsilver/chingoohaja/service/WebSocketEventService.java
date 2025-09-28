package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.dto.call.CallStartInfo;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingNotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketEventService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    public void sendMatchingSuccessNotification(Long userId, Long callId, Long partnerId, String partnerNickname) {
        try {
            MatchingNotificationResponse response = MatchingNotificationResponse.success(
                    callId, partnerId, partnerNickname
            );

//            messagingTemplate.convertAndSendToUser(
//                    String.valueOf(userId),
//                    "/queue/matching",
//                    response
//            );
            sendMessageWithDetailedLogging(userId, "/queue/matching", response, "매칭 성공");

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

    public void sendCallStartNotification(Long userId, CallStartInfo callStartInfo) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/call-start",
                    callStartInfo
            );
            log.debug("통화 시작 알림 전송 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("통화 시작 알림 전송 실패 - userId: {}", userId, e);
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
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/personal",
                    message
            );

            log.debug("개인 메시지 전송 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("개인 메시지 전송 실패 - userId: {}", userId, e);
        }
    }

    public record CallStatusUpdateMessage(
            Long callId,
            String status
    ) {}


    private void sendMessageWithDetailedLogging(Long userId, String destination, Object payload, String messageType) {
        String userIdStr = String.valueOf(userId);

        try {
            log.info("=== {} 메시지 전송 시작 ===", messageType);
            log.info("Target User: {}", userIdStr);
            log.info("Destination: {}", destination);
            log.info("Payload Type: {}", payload.getClass().getSimpleName());
            log.info("Payload: {}", payload);

            // 1. 사용자 연결 상태 확인
            SimpUser user = userRegistry.getUser(userIdStr);
            if (user != null) {
                log.info("✅ 사용자 연결됨 - Sessions: {}", user.getSessions().size());
                user.getSessions().forEach(session ->
                        log.info("  Session: {} (subscriptions: {})",
                                session.getId(), session.getSubscriptions().size())
                );
            } else {
                log.warn("❌ 사용자 연결되지 않음: {}", userIdStr);

                // 연결된 모든 사용자 출력
                log.info("현재 연결된 사용자들:");
                userRegistry.getUsers().forEach(connectedUser ->
                        log.info("  - User: {} (Sessions: {})",
                                connectedUser.getName(), connectedUser.getSessions().size())
                );
            }

            // 2. 메시지 전송
            log.info("메시지 전송 시도...");
            messagingTemplate.convertAndSendToUser(userIdStr, destination, payload);
            log.info("✅ 메시지 전송 완료");

            // 3. 전송 후 약간의 지연을 두고 재확인
            Thread.sleep(50);
            SimpUser userAfter = userRegistry.getUser(userIdStr);
            if (userAfter != null) {
                log.info("✅ 전송 후 사용자 여전히 연결됨");
            } else {
                log.warn("⚠️ 전송 후 사용자 연결 끊어짐");
            }

            log.info("=== {} 메시지 전송 완료 ===", messageType);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("=== {} 메시지 전송 실패 (인터럽트) ===", messageType);
            log.error("User: {}, Destination: {}, Error: {}", userIdStr, destination, e.getMessage(), e);
            throw new IllegalStateException("메시지 전송 중 인터럽트 발생", e);
        } catch (RuntimeException e) {
            log.error("=== {} 메시지 전송 실패 ===", messageType);
            log.error("User: {}, Destination: {}, Error: {}", userIdStr, destination, e.getMessage(), e);
            throw e;
        }
    }
}
