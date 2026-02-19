package com.ldsilver.chingoohaja.scheduler;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.service.CallGracePeriodService;
import com.ldsilver.chingoohaja.service.CallStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GracePeriodScheduler 테스트")
class GracePeriodSchedulerTest {

    @Mock private CallRepository callRepository;
    @Mock private CallGracePeriodService gracePeriodService;
    @Mock private CallStatusService callStatusService;

    @InjectMocks private GracePeriodScheduler gracePeriodScheduler;

    private User user1;
    private User user2;
    private Call activeCall;

    @BeforeEach
    void setUp() {
        user1 = User.of("user1@test.com", "유저1", "유저일", Gender.MALE,
                LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k1");
        user2 = User.of("user2@test.com", "유저2", "유저이", Gender.FEMALE,
                LocalDate.of(1992, 5, 15), null, UserType.USER, null, "kakao", "k2");
        setId(user1, 1L);
        setId(user2, 2L);

        Category category = Category.from("일상");
        setId(category, 1L);

        activeCall = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
        activeCall.startCall();
        setId(activeCall, 100L);
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

    @Nested
    @DisplayName("checkExpiredGracePeriods")
    class CheckExpiredGracePeriods {

        @Test
        @DisplayName("활성 통화가 없으면 유예 기간 체크를 수행하지 않는다")
        void givenNoActiveCalls_whenCheck_thenSkips() {
            // given
            when(callRepository.findByCallStatus(CallStatus.IN_PROGRESS))
                    .thenReturn(Collections.emptyList());

            // when
            gracePeriodScheduler.checkExpiredGracePeriods();

            // then
            verify(gracePeriodService, never()).isInGracePeriod(anyLong(), anyLong());
        }

        @Test
        @DisplayName("두 사용자 모두 유예 기간이 아니면 아무 작업도 하지 않는다")
        void givenBothUsersConnected_whenCheck_thenDoesNothing() {
            // given
            when(callRepository.findByCallStatus(CallStatus.IN_PROGRESS))
                    .thenReturn(List.of(activeCall));
            when(gracePeriodService.isInGracePeriod(100L, 1L)).thenReturn(false);
            when(gracePeriodService.isInGracePeriod(100L, 2L)).thenReturn(false);

            // when
            gracePeriodScheduler.checkExpiredGracePeriods();

            // then
            verify(gracePeriodService).isInGracePeriod(100L, 1L);
            verify(gracePeriodService).isInGracePeriod(100L, 2L);
            verifyNoInteractions(callStatusService);
        }

        @Test
        @DisplayName("한 사용자만 유예 기간이면 아무 작업도 하지 않는다")
        void givenOneUserInGrace_whenCheck_thenDoesNothing() {
            // given
            when(callRepository.findByCallStatus(CallStatus.IN_PROGRESS))
                    .thenReturn(List.of(activeCall));
            when(gracePeriodService.isInGracePeriod(100L, 1L)).thenReturn(true);
            when(gracePeriodService.isInGracePeriod(100L, 2L)).thenReturn(false);

            // when
            gracePeriodScheduler.checkExpiredGracePeriods();

            // then
            verifyNoInteractions(callStatusService);
        }

        @Test
        @DisplayName("두 사용자 모두 유예 기간이면 두 사용자의 유예 기간 상태를 체크한다")
        void givenBothUsersInGrace_whenCheck_thenChecksGracePeriodForBothUsers() {
            // given
            when(callRepository.findByCallStatus(CallStatus.IN_PROGRESS))
                    .thenReturn(List.of(activeCall));
            when(gracePeriodService.isInGracePeriod(100L, 1L)).thenReturn(true);
            when(gracePeriodService.isInGracePeriod(100L, 2L)).thenReturn(true);

            // when
            gracePeriodScheduler.checkExpiredGracePeriods();

            // then
            verify(gracePeriodService).isInGracePeriod(100L, 1L);
            verify(gracePeriodService).isInGracePeriod(100L, 2L);
        }

        @Test
        @DisplayName("유예 기간 체크 중 예외가 발생해도 나머지 통화는 계속 처리한다")
        void givenExceptionDuringCheck_whenMultipleCalls_thenContinuesProcessing() {
            // given
            Call anotherCall = Call.from(user1, user2, Category.from("여행"), CallType.RANDOM_MATCH);
            anotherCall.startCall();
            setId(anotherCall, 200L);

            when(callRepository.findByCallStatus(CallStatus.IN_PROGRESS))
                    .thenReturn(List.of(activeCall, anotherCall));

            when(gracePeriodService.isInGracePeriod(eq(100L), anyLong()))
                    .thenThrow(new RuntimeException("Redis 오류"));
            when(gracePeriodService.isInGracePeriod(eq(200L), anyLong())).thenReturn(false);

            // when (예외가 전파되지 않아야 함)
            gracePeriodScheduler.checkExpiredGracePeriods();

            // then - 200L 통화는 정상 처리됨
            verify(gracePeriodService, atLeastOnce()).isInGracePeriod(eq(200L), anyLong());
        }
    }
}
