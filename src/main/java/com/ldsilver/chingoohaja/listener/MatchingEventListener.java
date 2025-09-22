package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.event.MatchingSuccessEvent;
import com.ldsilver.chingoohaja.service.RedisMatchingQueueService;
import com.ldsilver.chingoohaja.service.WebSocketEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingEventListener {

    private final RedisMatchingQueueService redisMatchingQueueService;
    private final WebSocketEventService webSocketEventService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMatchingSuccess(MatchingSuccessEvent event) {
        log.debug("매칭 성공 이벤트 처리 시작 - callId: {}", event.getCallId());

        try {
            // 1. Redis에서 매칭된 사용자들 제거
            RedisMatchingQueueService.RemoveUserResult removeResult =
                    redisMatchingQueueService.removeMatchedUsers(event.getCategoryId(), event.getUserIds());

            if (!removeResult.success()) {
                log.warn("Redis 사용자 제거 실패 - categoryId: {}, userIds: {}, reason: {}",
                        event.getCategoryId(), event.getUserIds(), removeResult.message());
                // DB는 성공했으므로 매칭은 진행하되, Redis 정리만 실패한 상황
            } else {
                log.debug("Redis 사용자 제거 성공 - categoryId: {}, removed: {}",
                        event.getCategoryId(), removeResult.removedCount());
            }

            // 2. WebSocket 매칭 성공 알림 전송
            sendMatchingSuccessNotifications(event);

            log.info("매칭 성공 후처리 완료 - callId: {}", event.getCallId());

        } catch (Exception e) {
            log.error("매칭 성공 후처리 실패 - callId: {}", event.getCallId(), e);
        }
    }

    private void sendMatchingSuccessNotifications(MatchingSuccessEvent event) {
        try {
            // 각 사용자에게 상대방 정보와 함께 매칭 성공 알림
            webSocketEventService.sendMatchingSuccessNotification(
                    event.getUser1().getId(),
                    event.getCallId(),
                    event.getUser2().getId(),
                    event.getUser2().getNickname()
            );

            webSocketEventService.sendMatchingSuccessNotification(
                    event.getUser2().getId(),
                    event.getCallId(),
                    event.getUser1().getId(),
                    event.getUser1().getNickname()
            );

            log.debug("매칭 성공 알림 전송 완료 - callId: {}, users: [{}, {}]",
                    event.getCallId(), event.getUser1().getId(), event.getUser2().getId());

        } catch (Exception e) {
            log.error("매칭 성공 알림 전송 실패 - callId: {}", event.getCallId(), e);
        }
    }
}
