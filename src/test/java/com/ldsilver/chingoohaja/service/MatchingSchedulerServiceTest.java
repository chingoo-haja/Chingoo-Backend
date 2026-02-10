package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.config.MatchingSchedulerProperties;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingSchedulerService 테스트")
class MatchingSchedulerServiceTest {

    @Mock private RedisMatchingQueueService redisMatchingQueueService;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CallRepository callRepository;
    @Mock private MatchingQueueRepository matchingQueueRepository;
    @Mock private WebSocketEventService webSocketEventService;
    @Mock private MatchingSchedulerProperties schedulerProperties;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private MatchingSchedulerService matchingSchedulerService;

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

    @Nested
    @DisplayName("processMatchingForCategory")
    class ProcessMatchingForCategory {

        @Test
        @DisplayName("대기 인원이 2명 이상이면 매칭을 성공적으로 처리한다")
        void givenSufficientWaitingUsers_whenProcess_thenMatchesSuccessfully() {
            // given
            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(2L);
            when(redisMatchingQueueService.findMatchCandidates(1L, 2))
                    .thenReturn(new RedisMatchingQueueService.MatchCandicateResult(true, "성공", List.of(1L, 2L)));
            when(redisMatchingQueueService.isBlocked(1L, 2L)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

            Call savedCall = Call.from(user1, user2, category, CallType.RANDOM_MATCH);
            setId(savedCall, 100L);
            savedCall.startCall();
            when(callRepository.save(any(Call.class))).thenReturn(savedCall);

            when(matchingQueueRepository.findByUserOrderByCreatedAtDesc(any(User.class)))
                    .thenReturn(Collections.emptyList());

            // when
            boolean result = matchingSchedulerService.processMatchingForCategory(category);

            // then
            assertThat(result).isTrue();
            verify(callRepository).save(any(Call.class));
            verify(eventPublisher).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("대기 인원이 2명 미만이면 매칭하지 않는다")
        void givenInsufficientWaitingUsers_whenProcess_thenReturnsFalse() {
            // given
            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(1L);
            when(schedulerProperties.isDebugLogEnabled()).thenReturn(false);

            // when
            boolean result = matchingSchedulerService.processMatchingForCategory(category);

            // then
            assertThat(result).isFalse();
            verify(callRepository, never()).save(any());
        }

        @Test
        @DisplayName("차단된 사용자 쌍은 매칭하지 않고 다음 시도로 넘어간다")
        void givenBlockedUserPair_whenProcess_thenSkipsAndRetries() {
            // given
            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(2L);
            when(redisMatchingQueueService.findMatchCandidates(1L, 2))
                    .thenReturn(new RedisMatchingQueueService.MatchCandicateResult(true, "성공", List.of(1L, 2L)));
            when(redisMatchingQueueService.isBlocked(1L, 2L)).thenReturn(true);

            // when
            boolean result = matchingSchedulerService.processMatchingForCategory(category);

            // then
            assertThat(result).isFalse();
            verify(callRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 사용자가 매칭되면 건너뛴다")
        void givenSameUserMatched_whenProcess_thenSkips() {
            // given
            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(2L);
            when(redisMatchingQueueService.findMatchCandidates(1L, 2))
                    .thenReturn(new RedisMatchingQueueService.MatchCandicateResult(true, "성공", List.of(1L, 1L)));

            // when
            boolean result = matchingSchedulerService.processMatchingForCategory(category);

            // then
            assertThat(result).isFalse();
            verify(callRepository, never()).save(any());
        }

        @Test
        @DisplayName("사용자 조회 실패 시 큐에 복구하고 다음 시도로 넘어간다")
        void givenUserNotFound_whenProcess_thenRestoresAndRetries() {
            // given
            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(2L);
            when(redisMatchingQueueService.findMatchCandidates(1L, 2))
                    .thenReturn(new RedisMatchingQueueService.MatchCandicateResult(true, "성공", List.of(1L, 2L)));
            when(redisMatchingQueueService.isBlocked(1L, 2L)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());
            when(redisMatchingQueueService.enqueueUser(anyLong(), anyLong(), anyString()))
                    .thenReturn(new RedisMatchingQueueService.EnqueueResult(true, "복구 성공", 1));

            // when
            boolean result = matchingSchedulerService.processMatchingForCategory(category);

            // then
            assertThat(result).isFalse();
            verify(redisMatchingQueueService, atLeastOnce()).enqueueUser(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("매칭 후보 탐색이 실패하면 재시도한다")
        void givenCandidateSearchFails_whenProcess_thenRetries() {
            // given
            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(2L);
            when(redisMatchingQueueService.findMatchCandidates(1L, 2))
                    .thenReturn(new RedisMatchingQueueService.MatchCandicateResult(false, "후보 없음", List.of()));

            // when
            boolean result = matchingSchedulerService.processMatchingForCategory(category);

            // then
            assertThat(result).isFalse();
            verify(redisMatchingQueueService, times(5)).findMatchCandidates(1L, 2);
        }
    }

    @Nested
    @DisplayName("processMatching")
    class ProcessMatching {

        @Test
        @DisplayName("활성 카테고리별로 매칭을 처리한다")
        void givenActiveCategories_whenProcessMatching_thenProcessesEach() {
            // given
            when(schedulerProperties.isDebugLogEnabled()).thenReturn(false);
            when(categoryRepository.findByIsActiveTrueOrderByName()).thenReturn(List.of(category));

            // TransactionTemplate.execute() 호출 시 실제 메서드를 호출하도록 설정
            when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

            when(redisMatchingQueueService.getWaitingCount(1L)).thenReturn(1L);

            // when
            matchingSchedulerService.processMatching();

            // then
            verify(categoryRepository).findByIsActiveTrueOrderByName();
        }

        @Test
        @DisplayName("활성 카테고리가 없으면 매칭을 수행하지 않는다")
        void givenNoActiveCategories_whenProcessMatching_thenSkips() {
            // given
            when(schedulerProperties.isDebugLogEnabled()).thenReturn(false);
            when(categoryRepository.findByIsActiveTrueOrderByName()).thenReturn(Collections.emptyList());

            // when
            matchingSchedulerService.processMatching();

            // then
            verify(redisMatchingQueueService, never()).getWaitingCount(anyLong());
        }
    }

    @Nested
    @DisplayName("cleanupExpiredQueues")
    class CleanupExpiredQueues {

        @Test
        @DisplayName("만료된 큐를 정리하고 알림을 전송한다")
        void givenExpiredQueues_whenCleanup_thenUpdatesAndNotifies() {
            // given
            when(schedulerProperties.getExpiredTime()).thenReturn(600);

            MatchingQueue expiredQueue = MatchingQueue.of(user1, category, QueueType.RANDOM_MATCH, QueueStatus.WAITING, "queue_1");
            when(matchingQueueRepository.findExpiredQueues(any(LocalDateTime.class)))
                    .thenReturn(List.of(expiredQueue));
            when(matchingQueueRepository.updateExpiredQueues(eq(QueueStatus.EXPIRED), any(LocalDateTime.class)))
                    .thenReturn(1);

            // when
            matchingSchedulerService.cleanupExpiredQueues();

            // then
            verify(matchingQueueRepository).updateExpiredQueues(eq(QueueStatus.EXPIRED), any(LocalDateTime.class));
            verify(webSocketEventService).sendMatchingCancelledNotification(eq(1L), anyString());
        }

        @Test
        @DisplayName("만료된 큐가 없으면 아무 작업도 하지 않는다")
        void givenNoExpiredQueues_whenCleanup_thenDoesNothing() {
            // given
            when(schedulerProperties.getExpiredTime()).thenReturn(600);
            when(matchingQueueRepository.findExpiredQueues(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // when
            matchingSchedulerService.cleanupExpiredQueues();

            // then
            verify(matchingQueueRepository, never()).updateExpiredQueues(any(), any());
            verify(webSocketEventService, never()).sendMatchingCancelledNotification(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("updateDailyMatchingStatistics")
    class UpdateDailyMatchingStatistics {

        @Test
        @DisplayName("일일 매칭 통계를 조회한다")
        void givenStatsExist_whenUpdate_thenLogsStatistics() {
            // given
            Object[] statsRow = new Object[]{10L, 20L};
            List<Object[]> statsList = Collections.singletonList(statsRow);
            when(matchingQueueRepository.getMatchingSuccessRate(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(statsList);

            // when
            matchingSchedulerService.updateDailyMatchingStatistics();

            // then
            verify(matchingQueueRepository).getMatchingSuccessRate(any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("통계 데이터가 없으면 조회만 하고 끝난다")
        void givenNoStats_whenUpdate_thenDoesNothing() {
            // given
            when(matchingQueueRepository.getMatchingSuccessRate(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // when
            matchingSchedulerService.updateDailyMatchingStatistics();

            // then
            verify(matchingQueueRepository).getMatchingSuccessRate(any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }
}
