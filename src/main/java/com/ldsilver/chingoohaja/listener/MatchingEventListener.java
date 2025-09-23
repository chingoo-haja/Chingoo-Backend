package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.AgoraHealthStatus;
import com.ldsilver.chingoohaja.dto.call.CallStartInfo;
import com.ldsilver.chingoohaja.dto.call.response.BatchTokenResponse;
import com.ldsilver.chingoohaja.dto.call.response.ChannelResponse;
import com.ldsilver.chingoohaja.event.MatchingSuccessEvent;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.service.*;
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
    private final CallRepository callRepository;
    private final CallChannelService callChannelService;
    private final AgoraTokenService agoraTokenService;
    private final AgoraService agoraService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMatchingSuccess(MatchingSuccessEvent event) {
        log.debug("매칭 성공 이벤트 처리 시작 - callId: {}", event.getCallId());

        AgoraHealthStatus agoraStatus = agoraService.checkHealth();
        if (!agoraStatus.canMakeCalls()) {
            log.error("매칭 성공했지만 통화 기능 사용 불가 - callId: {}, status: {}",
                    event.getCallId(), agoraStatus.statusMessage());

            // 사용자들에게 오류 알림 전송
            sendServiceErrorNotifications(event);
            return;
        }

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

            // 2. Call 정보 조회
            Call call = callRepository.findById(event.getCallId()).orElse(null);
            if (call == null) {
                log.error("Call 조회 실패 - callId: {}", event.getCallId());
                return;
            }

            // 3. Agora 채널 생성
            ChannelResponse channelResponse = callChannelService.createChannel(call);
            log.debug("Agora 채널 생성 완료 - callId: {}, channelName: {}",
                    event.getCallId(), channelResponse.channelName());

            // 4. 토큰 생성
            BatchTokenResponse tokenResponse = agoraTokenService.generateTokenForMatching(call);
            log.debug("Agora 토큰 생성 완료 - callId: {}", event.getCallId());

            // 5. WebSocket 매칭 성공 알림 전송
            sendMatchingSuccessNotifications(event);
            sendCallStartNotifications(event, channelResponse, tokenResponse);

            log.info("매칭 성공 후처리 완료 - callId: {}", event.getCallId());

        } catch (Exception e) {
            log.error("매칭 성공 후처리 실패 - callId: {}", event.getCallId(), e);
        }
    }


    private void sendCallStartNotifications(MatchingSuccessEvent event,
                                            ChannelResponse channelResponse,
                                            BatchTokenResponse tokenResponse) {
        CallStartInfo user1CallInfo = new CallStartInfo(
                event.getCallId(),
                event.getUser2().getId(),
                event.getUser2().getNickname(),
                channelResponse.channelName(),
                tokenResponse.user1Token().rtcToken(),
                tokenResponse.user1Token().agoraUid(),
                tokenResponse.user1Token().expiresAt()
        );

        try {
            webSocketEventService.sendCallStartNotification(
                    event.getUser1().getId(), user1CallInfo
            );
        } catch (Exception e) {
            log.error("통화 시작 알림 전송 실패(user1) - callId: {}", event.getCallId(), e);
        }

        CallStartInfo user2CallInfo = new CallStartInfo(
                event.getCallId(),
                event.getUser1().getId(),
                event.getUser1().getNickname(),
                channelResponse.channelName(),
                tokenResponse.user2Token().rtcToken(),
                tokenResponse.user2Token().agoraUid(),
                tokenResponse.user2Token().expiresAt()
        );

        try {
            webSocketEventService.sendCallStartNotification(
                    event.getUser2().getId(), user2CallInfo
            );
        } catch (Exception e) {
            log.error("통화 시작 알림 전송 실패(user2) - callId: {}", event.getCallId(), e);
        }

        log.debug("통화 시작 알림 전송 완료 - callId: {}, users: [{}, {}]",
                event.getCallId(), event.getUser1().getId(), event.getUser2().getId());
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

    private void sendServiceErrorNotifications(MatchingSuccessEvent event) {
        try {
            webSocketEventService.sendMatchingCancelledNotification(
                    event.getUser1().getId(), "일시적으로 통화 서비스에 문제가 발생했습니다.");
            webSocketEventService.sendMatchingCancelledNotification(
                    event.getUser2().getId(), "일시적으로 통화 서비스에 문제가 발생했습니다.");
        } catch (Exception e) {
            log.error("서비스 오류 알림 전송 실패", e);
        }
    }
}
