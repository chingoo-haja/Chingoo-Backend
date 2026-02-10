package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.RecordingProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallSessionRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CallService 테스트")
class CallServiceTest {

    @Mock private CallRepository callRepository;
    @Mock private CallRecordingRepository callRecordingRepository;
    @Mock private AgoraRecordingService agoraRecordingService;
    @Mock private RecordingProperties recordingProperties;
    @Mock private CallSessionRepository callSessionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CallService callService;

    private User user1;
    private User user2;
    private Category category;

    @BeforeEach
    void setUp() {
        user1 = User.of("user1@test.com", "유저1", "유저일", Gender.MALE, LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k1");
        user2 = User.of("user2@test.com", "유저2", "유저이", Gender.FEMALE, LocalDate.of(1992, 5, 15), null, UserType.USER, null, "kakao", "k2");
        setId(user1, 1L);
        setId(user2, 2L);

        category = createCategory(1L, "일상");
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

    private Category createCategory(Long id, String name) {
        Category cat = Category.from(name);
        setId(cat, id);
        return cat;
    }

    private Call createReadyCall() {
        Call call = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
        setId(call, 100L);
        return call;
    }

    private Call createInProgressCall() {
        Call call = createReadyCall();
        call.startCall();
        return call;
    }

    @Nested
    @DisplayName("startCall")
    class StartCall {

        @Test
        @DisplayName("READY 상태의 통화를 시작한다")
        void givenReadyCall_whenStartCall_thenChangesToInProgress() {
            // given
            Call call = createReadyCall();
            when(callRepository.findByIdWithLock(100L)).thenReturn(Optional.of(call));
            when(callRepository.save(any(Call.class))).thenReturn(call);
            when(recordingProperties.isAutoStart()).thenReturn(false);

            // when
            callService.startCall(100L);

            // then
            assertThat(call.getCallStatus()).isEqualTo(CallStatus.IN_PROGRESS);
            verify(callRepository).save(call);
        }

        @Test
        @DisplayName("이미 진행 중인 통화는 중복 시작을 무시한다")
        void givenInProgressCall_whenStartCall_thenIgnoresDuplicate() {
            // given
            Call call = createInProgressCall();
            when(callRepository.findByIdWithLock(100L)).thenReturn(Optional.of(call));

            // when
            callService.startCall(100L);

            // then
            verify(callRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 통화이면 예외를 던진다")
        void givenNonExistentCall_whenStartCall_thenThrowsException() {
            // given
            when(callRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> callService.startCall(999L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CALL_NOT_FOUND));
        }

        @Test
        @DisplayName("COMPLETED 상태에서는 통화를 시작할 수 없다")
        void givenCompletedCall_whenStartCall_thenThrowsException() {
            // given
            Call call = createInProgressCall();
            call.endCall();
            when(callRepository.findByIdWithLock(100L)).thenReturn(Optional.of(call));

            // when & then
            assertThatThrownBy(() -> callService.startCall(100L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CALL_START_FAILED));
        }

        @Test
        @DisplayName("autoStart가 활성화되고 채널명이 있으면 CallStartedEvent를 발행한다")
        void givenAutoStartEnabled_whenStartCall_thenPublishesEvent() {
            // given
            Call call = createReadyCall();
            call.setAgoraChannelInfo("test_channel");
            when(callRepository.findByIdWithLock(100L)).thenReturn(Optional.of(call));
            when(callRepository.save(any(Call.class))).thenReturn(call);
            when(recordingProperties.isAutoStart()).thenReturn(true);

            // when
            callService.startCall(100L);

            // then
            verify(eventPublisher).publishEvent(any(Object.class));
        }
    }

    @Nested
    @DisplayName("endCall")
    class EndCall {

        @Test
        @DisplayName("진행 중인 통화를 종료한다")
        void givenInProgressCall_whenEndCall_thenChangesToCompleted() {
            // given
            Call call = createInProgressCall();
            when(callRepository.findById(100L)).thenReturn(Optional.of(call));
            when(recordingProperties.isAutoStop()).thenReturn(false);
            when(callSessionRepository.endAllSessionsForCall(anyLong(), any(LocalDateTime.class))).thenReturn(2);
            when(callRepository.save(any(Call.class))).thenReturn(call);

            // when
            callService.endCall(100L);

            // then
            assertThat(call.getCallStatus()).isEqualTo(CallStatus.COMPLETED);
            assertThat(call.getDurationSeconds()).isNotNull();
            verify(callRepository).save(call);
        }

        @Test
        @DisplayName("이미 종료된 통화는 중복 종료를 무시한다")
        void givenCompletedCall_whenEndCall_thenIgnoresDuplicate() {
            // given
            Call call = createInProgressCall();
            call.endCall();
            when(callRepository.findById(100L)).thenReturn(Optional.of(call));

            // when
            callService.endCall(100L);

            // then
            verify(callRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 통화이면 예외를 던진다")
        void givenNonExistentCall_whenEndCall_thenThrowsException() {
            // given
            when(callRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> callService.endCall(999L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CALL_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("createCallFromMatching")
    class CreateCallFromMatching {

        @Test
        @DisplayName("매칭된 사용자들로 통화를 생성하고 시작한다")
        void givenMatchedUsers_whenCreate_thenCreatesAndStartsCall() {
            // given
            when(callRepository.save(any(Call.class))).thenAnswer(invocation -> {
                Call call = invocation.getArgument(0);
                setId(call, 200L);
                return call;
            });
            when(callRepository.findByIdWithLock(200L)).thenReturn(Optional.of(Call.from(user1, user2, category, CallType.RANDOM_MATCH)));
            when(recordingProperties.isAutoStart()).thenReturn(false);

            // when
            Call result = callService.createCallFromMatching(user1, user2, category);

            // then
            assertThat(result).isNotNull();
            verify(callRepository, atLeast(1)).save(any(Call.class));
        }
    }

    @Nested
    @DisplayName("forceEndCallByAdmin")
    class ForceEndCallByAdmin {

        @Test
        @DisplayName("관리자가 진행 중인 통화를 강제 종료한다")
        void givenInProgressCall_whenForceEnd_thenEndsCall() {
            // given
            Call call = createInProgressCall();
            when(callRepository.findByIdWithLockAndFetchUsers(100L)).thenReturn(Optional.of(call));
            when(recordingProperties.isAutoStop()).thenReturn(false);
            when(callSessionRepository.endAllSessionsForCall(anyLong(), any(LocalDateTime.class))).thenReturn(2);
            when(callRepository.save(any(Call.class))).thenReturn(call);

            // when
            var response = callService.forceEndCallByAdmin(100L, 99L);

            // then
            assertThat(response).isNotNull();
            assertThat(call.getCallStatus()).isEqualTo(CallStatus.COMPLETED);
        }

        @Test
        @DisplayName("이미 종료된 통화는 강제 종료할 수 없다")
        void givenCompletedCall_whenForceEnd_thenThrowsException() {
            // given
            Call call = createInProgressCall();
            call.endCall();
            when(callRepository.findByIdWithLockAndFetchUsers(100L)).thenReturn(Optional.of(call));

            // when & then
            assertThatThrownBy(() -> callService.forceEndCallByAdmin(100L, 99L))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CALL_ALREADY_ENDED));
        }
    }
}
