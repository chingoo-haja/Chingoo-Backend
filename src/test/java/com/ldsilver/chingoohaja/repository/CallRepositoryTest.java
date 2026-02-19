package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("CallRepository 테스트")
class CallRepositoryTest {

    @Autowired private CallRepository callRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TestEntityManager entityManager;

    private User user1;
    private User user2;
    private User user3;
    private Category category1;
    private Category category2;

    @BeforeEach
    void setUp() {
        callRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        user1 = userRepository.save(User.of("user1@test.com", "유저1닉", "유저1",
                Gender.MALE, LocalDate.of(1990, 1, 1), null,
                UserType.USER, null, "kakao", "k1"));
        user2 = userRepository.save(User.of("user2@test.com", "유저2닉", "유저2",
                Gender.FEMALE, LocalDate.of(1992, 5, 10), null,
                UserType.USER, null, "kakao", "k2"));
        user3 = userRepository.save(User.of("user3@test.com", "유저3닉", "유저3",
                Gender.MALE, LocalDate.of(1988, 12, 25), null,
                UserType.USER, null, "google", "g1"));

        category1 = categoryRepository.save(Category.of("일상대화", true, CategoryType.RANDOM));
        category2 = categoryRepository.save(Category.of("고민상담", true, CategoryType.GUARDIAN));
    }

    private void setDurationSeconds(Call call, Integer duration) {
        try {
            Field field = Call.class.getDeclaredField("durationSeconds");
            field.setAccessible(true);
            field.set(call, duration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setEndAt(Call call, LocalDateTime endAt) {
        try {
            Field field = Call.class.getDeclaredField("endAt");
            field.setAccessible(true);
            field.set(call, endAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Call createCompletedCall(User u1, User u2, Category category, int durationSeconds) {
        Call call = Call.of(u1, u2, category, CallType.RANDOM_MATCH, CallStatus.COMPLETED);
        setDurationSeconds(call, durationSeconds);
        setEndAt(call, LocalDateTime.now());
        return callRepository.save(call);
    }

    @Nested
    @DisplayName("findByUserAndStatus")
    class FindByUserAndStatus {

        @Test
        @DisplayName("사용자별 특정 상태의 통화를 페이징하여 조회한다")
        void givenUserAndStatus_whenFind_thenReturnsPagedCalls() {
            // given
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category1, 600);
            callRepository.save(Call.of(user1, user2, category1, CallType.RANDOM_MATCH, CallStatus.CANCELLED));

            // when
            Page<Call> result = callRepository.findByUserAndStatus(
                    user1, CallStatus.COMPLETED, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("user2로 참여한 통화도 조회된다")
        void givenCallAsUser2_whenFind_thenIncluded() {
            createCompletedCall(user2, user1, category1, 300); // user1이 user2 위치

            Page<Call> result = callRepository.findByUserAndStatus(
                    user1, CallStatus.COMPLETED, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByUser")
    class FindByUser {

        @Test
        @DisplayName("사용자의 모든 통화를 조회한다")
        void givenUser_whenFind_thenReturnsAllCalls() {
            createCompletedCall(user1, user2, category1, 300);
            callRepository.save(Call.of(user1, user3, category1, CallType.RANDOM_MATCH, CallStatus.CANCELLED));

            Page<Call> result = callRepository.findByUser(user1, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("countCompletedCallsBetween")
    class CountCompletedCallsBetween {

        @Test
        @DisplayName("기간 내 특정 상태의 통화 수를 반환한다")
        void givenCompletedCalls_whenCount_thenReturnsCount() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category2, 600);
            callRepository.save(Call.of(user2, user3, category1, CallType.RANDOM_MATCH, CallStatus.CANCELLED));

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            long count = callRepository.countCompletedCallsBetween(start, end, CallStatus.COMPLETED);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getCallStatsByCategory")
    class GetCallStatsByCategory {

        @Test
        @DisplayName("카테고리별 통화 통계를 반환한다")
        void givenCompletedCalls_whenGetStats_thenReturnsCategoryStats() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category1, 600);
            createCompletedCall(user2, user3, category2, 900);

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            List<Object[]> result = callRepository.getCallStatsByCategory(start, end);

            assertThat(result).hasSize(2);
            // 일상대화가 2건으로 첫 번째 (COUNT DESC)
            assertThat(result.get(0)[0]).isEqualTo("일상대화");
            assertThat(result.get(0)[1]).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("getUserCallStatsByCategory")
    class GetUserCallStatsByCategory {

        @Test
        @DisplayName("사용자별 카테고리 통화 통계를 반환한다")
        void givenUserCalls_whenGetStats_thenReturnsPerCategoryStats() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category2, 600);
            createCompletedCall(user2, user3, category1, 900); // user1 미참여

            List<Object[]> result = callRepository.getUserCallStatsByCategory(user1.getId());

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByAgoraChannelName")
    class FindByAgoraChannelName {

        @Test
        @DisplayName("Agora 채널명으로 통화를 조회한다")
        void givenChannelName_whenFind_thenReturnsCall() {
            Call call = Call.of(user1, user2, category1, CallType.RANDOM_MATCH, CallStatus.READY);
            call.setAgoraChannelInfo("test-channel-123");
            callRepository.save(call);

            Optional<Call> result = callRepository.findByAgoraChannelName("test-channel-123");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 채널명이면 빈 결과를 반환한다")
        void givenNonExistingChannel_whenFind_thenReturnsEmpty() {
            Optional<Call> result = callRepository.findByAgoraChannelName("non-existing");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveCallsByUserId")
    class FindActiveCallsByUserId {

        @Test
        @DisplayName("사용자의 활성 통화를 조회한다 (READY, IN_PROGRESS)")
        void givenActiveCalls_whenFind_thenReturnsActive() {
            callRepository.save(Call.of(user1, user2, category1, CallType.RANDOM_MATCH, CallStatus.READY));
            callRepository.save(Call.of(user1, user3, category1, CallType.RANDOM_MATCH, CallStatus.IN_PROGRESS));
            createCompletedCall(user1, user2, category2, 300);

            List<Call> result = callRepository.findActiveCallsByUserId(user1.getId());

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("countByUserAndStatusAndDateBetween")
    class CountByUserAndStatusAndDateBetween {

        @Test
        @DisplayName("기간 내 사용자의 특정 상태 통화 수를 반환한다")
        void givenCalls_whenCount_thenReturnsUserStatusCount() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category2, 600);

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            long count = callRepository.countByUserAndStatusAndDateBetween(
                    user1, CallStatus.COMPLETED, start, end);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("sumDurationByUserAndDateBetween")
    class SumDurationByUserAndDateBetween {

        @Test
        @DisplayName("기간 내 사용자의 총 통화 시간을 반환한다")
        void givenCalls_whenSum_thenReturnsTotalDuration() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category2, 600);

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            long totalDuration = callRepository.sumDurationByUserAndDateBetween(user1, start, end);

            assertThat(totalDuration).isEqualTo(900);
        }

        @Test
        @DisplayName("통화가 없으면 0을 반환한다")
        void givenNoCalls_whenSum_thenReturnsZero() {
            long totalDuration = callRepository.sumDurationByUserAndDateBetween(
                    user1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

            assertThat(totalDuration).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findCallsBetweenUsers")
    class FindCallsBetweenUsers {

        @Test
        @DisplayName("두 사용자 간의 특정 상태 통화 목록을 반환한다")
        void givenCallsBetweenUsers_whenFind_thenReturnsCalls() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user2, user1, category2, 600); // 역방향도 포함

            List<Call> result = callRepository.findCallsBetweenUsers(
                    user1.getId(), user2.getId(), CallStatus.COMPLETED);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("제3자 통화는 포함하지 않는다")
        void givenThirdPartyCalls_whenFind_thenExcludes() {
            createCompletedCall(user1, user3, category1, 300); // user2 미참여

            List<Call> result = callRepository.findCallsBetweenUsers(
                    user1.getId(), user2.getId(), CallStatus.COMPLETED);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findLastCompletedCallBetweenUsers")
    class FindLastCompletedCallBetweenUsers {

        @Test
        @DisplayName("두 사용자 간의 마지막 완료 통화를 반환한다")
        void givenCompletedCalls_whenFind_thenReturnsLast() {
            createCompletedCall(user1, user2, category1, 300);

            Optional<Call> result = callRepository.findLastCompletedCallBetweenUsers(
                    user1.getId(), user2.getId());

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("완료 통화가 없으면 빈 결과를 반환한다")
        void givenNoCalls_whenFind_thenReturnsEmpty() {
            Optional<Call> result = callRepository.findLastCompletedCallBetweenUsers(
                    user1.getId(), user2.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCallStatus")
    class FindByCallStatus {

        @Test
        @DisplayName("특정 상태의 모든 통화를 조회한다")
        void givenStatus_whenFind_thenReturnsCalls() {
            callRepository.save(Call.of(user1, user2, category1, CallType.RANDOM_MATCH, CallStatus.READY));
            callRepository.save(Call.of(user2, user3, category1, CallType.RANDOM_MATCH, CallStatus.READY));
            createCompletedCall(user1, user3, category2, 300);

            List<Call> result = callRepository.findByCallStatus(CallStatus.READY);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("countByCallStatus")
    class CountByCallStatus {

        @Test
        @DisplayName("특정 상태의 통화 수를 반환한다")
        void givenCalls_whenCount_thenReturnsCount() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user2, user3, category1, 600);
            callRepository.save(Call.of(user1, user3, category2, CallType.RANDOM_MATCH, CallStatus.CANCELLED));

            int count = callRepository.countByCallStatus(CallStatus.COMPLETED);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("countCompletedCallsByUser")
    class CountCompletedCallsByUser {

        @Test
        @DisplayName("사용자의 완료된 통화 수를 반환한다")
        void givenCompletedCalls_whenCount_thenReturnsUserCount() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category2, 600);
            createCompletedCall(user2, user3, category1, 900); // user1 미참여

            int count = callRepository.countCompletedCallsByUser(user1);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getAverageCallDurationByCategory")
    class GetAverageCallDurationByCategory {

        @Test
        @DisplayName("카테고리별 평균 통화 시간을 반환한다")
        void givenCalls_whenGetAvg_thenReturnsAverageDuration() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category1, 600);

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            Double avg = callRepository.getAverageCallDurationByCategory(
                    category1.getId(), start, end);

            assertThat(avg).isEqualTo(450.0);
        }

        @Test
        @DisplayName("통화가 없으면 null을 반환한다")
        void givenNoCalls_whenGetAvg_thenReturnsNull() {
            Double avg = callRepository.getAverageCallDurationByCategory(
                    category1.getId(),
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(1));

            assertThat(avg).isNull();
        }
    }

    @Nested
    @DisplayName("countShortCallsSince")
    class CountShortCallsSince {

        @Test
        @DisplayName("기간 내 짧은 통화 수를 반환한다")
        void givenShortCalls_whenCount_thenReturnsCount() {
            // 짧은 통화 (60초 미만)
            Call shortCall = createCompletedCall(user1, user2, category1, 30);
            setEndAt(shortCall, LocalDateTime.now());
            callRepository.save(shortCall);

            // 긴 통화
            createCompletedCall(user1, user3, category1, 600);

            long count = callRepository.countShortCallsSince(
                    LocalDateTime.now().minusDays(1), 60);

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findStaleInProgressCalls")
    class FindStaleInProgressCalls {

        @Test
        @DisplayName("오래된 진행 중 통화를 조회한다")
        void givenStaleCalls_whenFind_thenReturnsStaleCalls() {
            // given
            Call call = Call.of(user1, user2, category1, CallType.RANDOM_MATCH, CallStatus.IN_PROGRESS);
            try {
                Field startAtField = Call.class.getDeclaredField("startAt");
                startAtField.setAccessible(true);
                startAtField.set(call, LocalDateTime.now().minusHours(3));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            callRepository.save(call);

            // when
            List<Call> result = callRepository.findStaleInProgressCalls(
                    LocalDateTime.now().minusHours(2));

            // then - startAt은 조건이 아니라 createdAt 기준이므로 확인
            // 쿼리는 startAt < threshold 이므로 3시간 전 시작 → 2시간 전보다 이전
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getAverageDurationMinutesBetween")
    class GetAverageDurationMinutesBetween {

        @Test
        @DisplayName("기간 내 평균 통화 시간을 분 단위로 반환한다")
        void givenCalls_whenGetAvg_thenReturnsMinutes() {
            createCompletedCall(user1, user2, category1, 300); // 5분
            createCompletedCall(user1, user3, category2, 600); // 10분

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            Double avgMinutes = callRepository.getAverageDurationMinutesBetween(start, end);

            assertThat(avgMinutes).isEqualTo(7.5); // (300+600)/2/60 = 7.5
        }
    }

    @Nested
    @DisplayName("countCallsByCategoryBetween")
    class CountCallsByCategoryBetween {

        @Test
        @DisplayName("카테고리별 기간 내 완료 통화 수를 반환한다")
        void givenCalls_whenCount_thenReturnsCount() {
            createCompletedCall(user1, user2, category1, 300);
            createCompletedCall(user1, user3, category1, 600);
            createCompletedCall(user2, user3, category2, 900);

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            long count = callRepository.countCallsByCategoryBetween(
                    category1.getId(), start, end);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findRecentEndedCalls")
    class FindRecentEndedCalls {

        @Test
        @DisplayName("최근 종료된 통화를 조회한다")
        void givenRecentCalls_whenFind_thenReturnsRecentEnded() {
            Call call = createCompletedCall(user1, user2, category1, 300);
            setEndAt(call, LocalDateTime.now());
            callRepository.save(call);

            List<Call> result = callRepository.findRecentEndedCalls(
                    LocalDateTime.now().minusHours(1));

            assertThat(result).hasSize(1);
        }
    }
}
