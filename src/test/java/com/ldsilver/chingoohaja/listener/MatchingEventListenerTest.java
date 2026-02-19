package com.ldsilver.chingoohaja.listener;

import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.call.AgoraHealthStatus;
import com.ldsilver.chingoohaja.dto.call.response.BatchTokenResponse;
import com.ldsilver.chingoohaja.dto.call.response.ChannelResponse;
import com.ldsilver.chingoohaja.dto.call.response.TokenResponse;
import com.ldsilver.chingoohaja.event.MatchingSuccessEvent;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallSessionRepository;
import com.ldsilver.chingoohaja.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingEventListener 테스트")
class MatchingEventListenerTest {

    @Mock private RedisMatchingQueueService redisMatchingQueueService;
    @Mock private WebSocketEventService webSocketEventService;
    @Mock private CallRepository callRepository;
    @Mock private CallChannelService callChannelService;
    @Mock private AgoraTokenService agoraTokenService;
    @Mock private AgoraService agoraService;
    @Mock private MatchingService matchingService;
    @Mock private CallSessionRepository callSessionRepository;
    @Mock private RecordingProperties recordingProperties;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MatchingEventListener matchingEventListener;

    private User user1;
    private User user2;
    private Category category;
    private Call call;
    private MatchingSuccessEvent event;

    @BeforeEach
    void setUp() {
        user1 = User.of("user1@test.com", "유저1", "유저일", Gender.MALE,
                LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k1");
        user2 = User.of("user2@test.com", "유저2", "유저이", Gender.FEMALE,
                LocalDate.of(1992, 5, 15), null, UserType.USER, null, "kakao", "k2");
        setId(user1, 1L);
        setId(user2, 2L);

        category = Category.from("일상");
        setId(category, 1L);

        call = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
        setId(call, 100L);

        event = new MatchingSuccessEvent(100L, 1L, List.of(1L, 2L), user1, user2);
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ChannelResponse buildChannelResponse(String channelName) {
        return new ChannelResponse(channelName, 100L, 2, 2,
                Set.of(1L, 2L), true, false, true,
                LocalDateTime.now().plusHours(1), LocalDateTime.now());
    }

    private TokenResponse buildTokenResponse(Long userId) {
        return TokenResponse.of("rtc-token-" + userId, null,
                "channel-abc", userId * 1000, userId, "PUBLISHER",
                LocalDateTime.now().plusHours(1));
    }

    @Nested
    @DisplayName("handleMatchingSuccess - Agora 정상 상태")
    class HandleMatchingSuccess {

        @BeforeEach
        void setUpHealthy() {
            AgoraHealthStatus healthy = AgoraHealthStatus.builder()
                    .isHealthy(true)
                    .tokenGenerationAvailable(true)
                    .restApiAvailable(true)
                    .statusMessage("OK")
                    .checkedAt(LocalDateTime.now())
                    .build();
            when(agoraService.checkHealth()).thenReturn(healthy);
        }

        @Test
        @DisplayName("매칭 성공 시 Redis 큐에서 사용자를 제거하고 WebSocket 알림을 전송한다")
        void givenMatchingSuccess_whenHandled_thenRemovesFromQueueAndNotifies() {
            // given
            when(redisMatchingQueueService.removeMatchedUsers(eq(1L), anyList()))
                    .thenReturn(new RedisMatchingQueueService.RemoveUserResult(true, "SUCCESS", 2));
            when(callRepository.findById(100L)).thenReturn(Optional.of(call));

            ChannelResponse channelResponse = buildChannelResponse("channel-abc");
            when(callChannelService.createChannel(any(Call.class))).thenReturn(channelResponse);
            when(callRepository.save(any(Call.class))).thenReturn(call);
            when(callChannelService.joinChannel(anyString(), anyLong())).thenReturn(channelResponse);

            BatchTokenResponse tokenResponse = new BatchTokenResponse(
                    buildTokenResponse(1L), buildTokenResponse(2L));
            when(agoraTokenService.generateTokenForMatching(any(Call.class))).thenReturn(tokenResponse);
            when(recordingProperties.isAutoStart()).thenReturn(false);

            // when
            matchingEventListener.handleMatchingSuccess(event);

            // then
            verify(redisMatchingQueueService).removeMatchedUsers(eq(1L), anyList());
            verify(webSocketEventService).sendMatchingSuccessNotification(eq(1L), eq(100L), eq(2L), anyString());
            verify(webSocketEventService).sendMatchingSuccessNotification(eq(2L), eq(100L), eq(1L), anyString());
            verify(webSocketEventService).sendCallStartNotification(eq(1L), any());
            verify(webSocketEventService).sendCallStartNotification(eq(2L), any());
        }

        @Test
        @DisplayName("자동 녹음이 활성화된 경우 CallStartedEvent를 발행한다")
        void givenAutoRecordingEnabled_whenMatchingSuccess_thenPublishesCallStartedEvent() {
            // given
            when(redisMatchingQueueService.removeMatchedUsers(anyLong(), anyList()))
                    .thenReturn(new RedisMatchingQueueService.RemoveUserResult(true, "SUCCESS", 2));
            when(callRepository.findById(100L)).thenReturn(Optional.of(call));

            ChannelResponse channelResponse = buildChannelResponse("channel-abc");
            when(callChannelService.createChannel(any(Call.class))).thenReturn(channelResponse);
            when(callRepository.save(any(Call.class))).thenReturn(call);
            when(callChannelService.joinChannel(anyString(), anyLong())).thenReturn(channelResponse);

            BatchTokenResponse tokenResponse = new BatchTokenResponse(
                    buildTokenResponse(1L), buildTokenResponse(2L));
            when(agoraTokenService.generateTokenForMatching(any(Call.class))).thenReturn(tokenResponse);
            when(recordingProperties.isAutoStart()).thenReturn(true);

            // when
            matchingEventListener.handleMatchingSuccess(event);

            // then
            verify(eventPublisher).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("Call 조회 실패 시 알림을 전송하지 않는다")
        void givenCallNotFound_whenHandled_thenSkipsNotifications() {
            // given
            when(redisMatchingQueueService.removeMatchedUsers(anyLong(), anyList()))
                    .thenReturn(new RedisMatchingQueueService.RemoveUserResult(true, "SUCCESS", 2));
            when(callRepository.findById(100L)).thenReturn(Optional.empty());

            // when
            matchingEventListener.handleMatchingSuccess(event);

            // then
            verify(webSocketEventService, never()).sendMatchingSuccessNotification(anyLong(), anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("채널 조인이 부분 실패하면 취소 알림을 전송하고 재매칭을 예약한다")
        void givenChannelJoinPartialFailure_whenHandled_thenNotifiesCancelAndSchedulesRematch() {
            // given
            when(redisMatchingQueueService.removeMatchedUsers(anyLong(), anyList()))
                    .thenReturn(new RedisMatchingQueueService.RemoveUserResult(true, "SUCCESS", 2));
            when(callRepository.findById(100L)).thenReturn(Optional.of(call));

            ChannelResponse channelResponse = buildChannelResponse("channel-abc");
            when(callChannelService.createChannel(any(Call.class))).thenReturn(channelResponse);
            when(callRepository.save(any(Call.class))).thenReturn(call);

            // 첫 번째 사용자는 성공, 두 번째는 실패
            when(callChannelService.joinChannel(anyString(), eq(1L))).thenReturn(channelResponse);
            when(callChannelService.joinChannel(anyString(), eq(2L)))
                    .thenThrow(new RuntimeException("조인 실패"));

            // when
            matchingEventListener.handleMatchingSuccess(event);

            // then
            verify(webSocketEventService, atLeastOnce())
                    .sendMatchingCancelledNotification(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("handleMatchingSuccess - Agora 불가 상태")
    class HandleMatchingSuccessWhenAgoraUnavailable {

        @Test
        @DisplayName("Agora 서비스 불가 시 오류 알림을 전송하고 재매칭을 예약한다")
        void givenAgoraUnavailable_whenHandled_thenSendsErrorAndSchedulesRematch() {
            // given
            AgoraHealthStatus unhealthy = AgoraHealthStatus.builder()
                    .isHealthy(false)
                    .tokenGenerationAvailable(false)
                    .restApiAvailable(false)
                    .statusMessage("서비스 불가")
                    .checkedAt(LocalDateTime.now())
                    .build();
            when(agoraService.checkHealth()).thenReturn(unhealthy);
            when(callRepository.findById(100L)).thenReturn(Optional.of(call));
            when(callRepository.save(any(Call.class))).thenReturn(call);

            // when
            matchingEventListener.handleMatchingSuccess(event);

            // then
            verify(webSocketEventService).sendMatchingCancelledNotification(eq(1L), anyString());
            verify(webSocketEventService).sendMatchingCancelledNotification(eq(2L), anyString());
            verify(callChannelService, never()).createChannel(any());
        }
    }
}
