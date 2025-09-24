package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.dto.call.AgoraHealthStatus;
import com.ldsilver.chingoohaja.dto.call.CallStartInfo;
import com.ldsilver.chingoohaja.dto.call.response.BatchTokenResponse;
import com.ldsilver.chingoohaja.dto.call.response.ChannelResponse;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingRequest;
import com.ldsilver.chingoohaja.event.MatchingSuccessEvent;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private MatchingService matchingService;

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

            // 4. 매칭된 사용자들을 채널에 자동 참가시킴
            Set<Long> joinedUserIds = joinUsersToChannel(event, channelResponse.channelName());

            // 5. 토큰 생성
            BatchTokenResponse tokenResponse = agoraTokenService.generateTokenForMatching(call);
            log.debug("Agora 토큰 생성 완료 - callId: {}", event.getCallId());

            // 6. WebSocket 매칭 성공 알림 전송
            sendMatchingSuccessNotifications(event);
            sendCallStartNotifications(event, channelResponse, tokenResponse, joinedUserIds);

            log.info("매칭 성공 후처리 완료 - callId: {}", event.getCallId());

        } catch (Exception e) {
            log.error("매칭 성공 후처리 실패 - callId: {}", event.getCallId(), e);
        }
    }


    private Set<Long> joinUsersToChannel(MatchingSuccessEvent event, String channelName) {
        Long user1Id = event.getUser1().getId();
        Long user2Id = event.getUser2().getId();
        Set<Long> joinedUserIds = new HashSet<>(2);

        boolean user1Success = attemptUserJoin(user1Id, channelName, event.getCallId());
        boolean user2Success = attemptUserJoin(user2Id, channelName, event.getCallId());

        if (user1Success) joinedUserIds.add(user1Id);
        if (user2Success) joinedUserIds.add(user2Id);

        if (joinedUserIds.size() < 2) {
            log.warn("채널 조인 전체 실패 - callId: {}, 성공한 사용자 수: {}/2",
                    event.getCallId(), joinedUserIds.size());

            // 성공한 사용자가 있다면 정리
            cleanupPartiallyJoinedUsers(joinedUserIds, channelName);

            // 매칭 무효화 및 재매칭 처리
            handleJoinFailure(event);

            return Collections.emptySet(); // 빈 Set 반환으로 알림 전송 방지
        }

        log.info("매칭된 사용자들 채널 조인 성공 - callId: {}, channelName: {}",
                event.getCallId(), channelName);
        return joinedUserIds;
    }

    private boolean attemptUserJoin(Long userId, String channelName, Long callId) {
        try {
            ChannelResponse response = callChannelService.joinChannel(channelName, userId);
            log.debug("사용자 채널 조인 성공 - callId: {}, userId: {}, participants: {}",
                    callId, userId, response.currentParticipants());
            return true;
        } catch (Exception e) {
            log.error("사용자 채널 조인 실패 - callId: {}, userId: {}, error: {}",
                    callId, userId, e.getMessage());
            return false;
        }
    }

    private void cleanupPartiallyJoinedUsers(Set<Long> joinedUserIds, String channelName) {
        for (Long userId : joinedUserIds) {
            try {
                callChannelService.leaveChannel(channelName, userId);
                log.info("부분 조인 실패로 인한 사용자 채널 정리 - userId: {}", userId);
            } catch (Exception e ){
                log.error("부분 조인 실패지만 사용자 채널 정리 실패 - userId: {}", userId);
            }
        }
    }

    private void handleJoinFailure(MatchingSuccessEvent event) {
        try {
            Call call = callRepository.findById(event.getCallId()).orElse(null);
            if (call != null) {
                log.info("채널 조인 실패로 인한 Call 무효화 - callId: {}", event.getCallId());
            }

            webSocketEventService.sendMatchingCancelledNotification(
                    event.getUser1().getId(), "통화 연결에 실패했습니다. 잠시 후 다시 시도해주세요.");
            webSocketEventService.sendMatchingCancelledNotification(
                    event.getUser2().getId(), "통화 연결에 실패했습니다. 잠시 후 다시 시도해주세요.");

            scheduleAutoRematch(event.getUser1().getId(), event.getCategoryId(), 5); // 5초 후
            scheduleAutoRematch(event.getUser2().getId(), event.getCategoryId(), 5);
        } catch (Exception e) {
            log.error("조인 실패 후처리 중 오류 발생 - callId : {}", event.getCallId(), e);
        }
    }


    private void sendCallStartNotifications(MatchingSuccessEvent event,
                                            ChannelResponse channelResponse,
                                            BatchTokenResponse tokenResponse,
                                            Set<Long> joinedUserIds) {
        if (joinedUserIds.size() != 2) {
            log.warn("조인된 사용자 수가 부족하여 CallStart 알림 전송 생략 - callId: {}, joinedCount: {}",
                    event.getCallId(), joinedUserIds.size());
            return;
        }

        // 두 사용자 모두 조인 성공한 경우에만 알림 전송
        CallStartInfo user1CallInfo = new CallStartInfo(
                event.getCallId(),
                event.getUser2().getId(),
                event.getUser2().getNickname(),
                channelResponse.channelName(),
                tokenResponse.user1Token().rtcToken(),
                tokenResponse.user1Token().agoraUid(),
                tokenResponse.user1Token().expiresAt()
        );

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
            webSocketEventService.sendCallStartNotification(event.getUser1().getId(), user1CallInfo);
            webSocketEventService.sendCallStartNotification(event.getUser2().getId(), user2CallInfo);
            log.info("통화 시작 알림 전송 완료 - callId: {}, users: [{}, {}]",
                    event.getCallId(), event.getUser1().getId(), event.getUser2().getId());
        } catch (Exception e) {
            log.error("통화 시작 알림 전송 실패 - callId: {}", event.getCallId(), e);
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

    private void scheduleAutoRematch(Long userId, Long categoryId, int delaySeconds) {
        log.info("자동 재매칭 스케줄링 - userId: {}, categoryId: {}, delay: {}초",
                userId, categoryId, delaySeconds);

        CompletableFuture
                .delayedExecutor(delaySeconds, TimeUnit.SECONDS)
                .execute(() -> {
                    try {
                        log.info("자동 재매칭 시도 - userId: {}, categoryId: {}", userId, categoryId);

                        MatchingRequest request = new MatchingRequest(categoryId);
                        matchingService.joinMatchingQueue(userId, request);

                        log.info("자동 재매칭 큐 등록 완료 - userId: {}", userId);

                    } catch (Exception e) {
                        log.warn("자동 재매칭 실패 - userId: {}, reason: {}", userId, e.getMessage());
                    }
                });
    }
}
