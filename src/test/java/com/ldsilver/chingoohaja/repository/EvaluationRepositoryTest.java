package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.category.enums.CategoryType;
import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("EvaluationRepository 테스트")
class EvaluationRepositoryTest {

    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private CallRepository callRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;

    private User evaluator;
    private User evaluated;
    private User thirdUser;
    private Category category;
    private Call call1;
    private Call call2;

    @BeforeEach
    void setUp() {
        evaluationRepository.deleteAll();
        callRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        evaluator = userRepository.save(User.of("evaluator@test.com", "평가자닉", "평가자",
                Gender.MALE, LocalDate.of(1990, 1, 1), null,
                UserType.USER, null, "kakao", "k1"));
        evaluated = userRepository.save(User.of("evaluated@test.com", "피평가자닉", "피평가자",
                Gender.FEMALE, LocalDate.of(1992, 5, 10), null,
                UserType.USER, null, "kakao", "k2"));
        thirdUser = userRepository.save(User.of("third@test.com", "제삼자닉", "제삼자",
                Gender.MALE, LocalDate.of(1988, 12, 25), null,
                UserType.USER, null, "google", "g1"));

        category = categoryRepository.save(Category.of("일상대화", true, CategoryType.RANDOM));

        call1 = callRepository.save(Call.of(evaluator, evaluated, category, CallType.RANDOM_MATCH, CallStatus.COMPLETED));
        call2 = callRepository.save(Call.of(evaluator, thirdUser, category, CallType.RANDOM_MATCH, CallStatus.COMPLETED));
    }

    @Nested
    @DisplayName("getPositiveFeedbackPercentageByUser")
    class GetPositiveFeedbackPercentageByUser {

        @Test
        @DisplayName("긍정 평가 비율을 반환한다")
        void givenEvaluations_whenGetPercentage_thenReturnsPositiveRate() {
            // given - 2개 긍정, 1개 부정 = 66.67%
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluated, FeedbackType.POSITIVE));

            // 추가 Call 생성하여 부정 평가 추가
            Call call3 = callRepository.save(Call.of(thirdUser, evaluated, category, CallType.RANDOM_MATCH, CallStatus.COMPLETED));
            evaluationRepository.save(Evaluation.of(call3, evaluator, evaluated, FeedbackType.NEGATIVE));

            // when
            Double percentage = evaluationRepository.getPositiveFeedbackPercentageByUser(evaluated);

            // then
            assertThat(percentage).isNotNull();
            // 2 positive / 3 total = 66.67%
            assertThat(percentage).isBetween(66.0, 67.0);
        }

        @Test
        @DisplayName("평가가 없으면 null을 반환한다")
        void givenNoEvaluations_whenGetPercentage_thenReturnsNull() {
            Double percentage = evaluationRepository.getPositiveFeedbackPercentageByUser(evaluated);

            assertThat(percentage).isNull();
        }
    }

    @Nested
    @DisplayName("getFeedbackStatsBetween")
    class GetFeedbackStatsBetween {

        @Test
        @DisplayName("기간별 피드백 타입 통계를 반환한다")
        void givenEvaluations_whenGetStats_thenReturnsFeedbackCounts() {
            // given
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluator, FeedbackType.NEGATIVE));

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            // when
            List<Object[]> result = evaluationRepository.getFeedbackStatsBetween(start, end);

            // then
            assertThat(result).hasSize(2); // POSITIVE, NEGATIVE
        }
    }

    @Nested
    @DisplayName("getUsersWithManyReports")
    class GetUsersWithManyReports {

        @Test
        @DisplayName("부정 평가가 임계값 이상인 사용자를 반환한다")
        void givenManyNegatives_whenFind_thenReturnsReportedUsers() {
            // given - evaluated에게 부정 평가 2개
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.NEGATIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluated, FeedbackType.NEGATIVE));

            // when - 임계값 2
            List<Object[]> result = evaluationRepository.getUsersWithManyReports(2);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("임계값 미만이면 반환하지 않는다")
        void givenFewNegatives_whenFind_thenReturnsEmpty() {
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.NEGATIVE));

            List<Object[]> result = evaluationRepository.getUsersWithManyReports(5);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCallAndEvaluatorAndEvaluated")
    class FindByCallAndEvaluatorAndEvaluated {

        @Test
        @DisplayName("특정 통화에서 특정 평가자의 평가를 조회한다")
        void givenEvaluation_whenFind_thenReturnsEvaluation() {
            // given
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));

            // when
            Optional<Evaluation> result = evaluationRepository
                    .findByCallAndEvaluatorAndEvaluated(call1, evaluator, evaluated);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getFeedbackType()).isEqualTo(FeedbackType.POSITIVE);
        }

        @Test
        @DisplayName("평가가 없으면 빈 결과를 반환한다")
        void givenNoEvaluation_whenFind_thenReturnsEmpty() {
            Optional<Evaluation> result = evaluationRepository
                    .findByCallAndEvaluatorAndEvaluated(call1, evaluator, evaluated);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByCall")
    class ExistsByCall {

        @Test
        @DisplayName("통화에 평가가 있으면 true를 반환한다")
        void givenEvaluation_whenCheck_thenReturnsTrue() {
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));

            assertThat(evaluationRepository.existsByCall(call1)).isTrue();
        }

        @Test
        @DisplayName("통화에 평가가 없으면 false를 반환한다")
        void givenNoEvaluation_whenCheck_thenReturnsFalse() {
            assertThat(evaluationRepository.existsByCall(call1)).isFalse();
        }
    }

    @Nested
    @DisplayName("countEvaluationsBetween")
    class CountEvaluationsBetween {

        @Test
        @DisplayName("기간 내 평가 수를 반환한다")
        void givenEvaluations_whenCount_thenReturnsCount() {
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluator, FeedbackType.NEGATIVE));

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            long count = evaluationRepository.countEvaluationsBetween(start, end);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getAverageSatisfactionByCategory")
    class GetAverageSatisfactionByCategory {

        @Test
        @DisplayName("카테고리별 만족도를 0~5 스케일로 반환한다")
        void givenEvaluations_whenGetSatisfaction_thenReturnsScaled() {
            // given - 2개 긍정 = 100% = 5.0점
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluator, FeedbackType.POSITIVE));

            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            // when
            Double satisfaction = evaluationRepository
                    .getAverageSatisfactionByCategory(category.getId(), start, end);

            // then
            assertThat(satisfaction).isEqualTo(5.0);
        }

        @Test
        @DisplayName("평가가 없으면 null을 반환한다")
        void givenNoEvaluations_whenGetSatisfaction_thenReturnsNull() {
            Double satisfaction = evaluationRepository
                    .getAverageSatisfactionByCategory(category.getId(),
                            LocalDateTime.now().minusDays(1),
                            LocalDateTime.now().plusDays(1));

            assertThat(satisfaction).isNull();
        }
    }

    @Nested
    @DisplayName("getAllUsersPositiveRates")
    class GetAllUsersPositiveRates {

        @Test
        @DisplayName("모든 사용자의 긍정 평가율을 내림차순으로 반환한다")
        void givenEvaluations_whenGet_thenReturnsAllRates() {
            // given
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluator, FeedbackType.NEGATIVE));

            // when
            List<Object[]> result = evaluationRepository.getAllUsersPositiveRates();

            // then
            assertThat(result).hasSize(2);
            // evaluated: 100% positive, evaluator: 0% positive
            // 첫 번째가 가장 높은 비율
        }
    }

    @Nested
    @DisplayName("getUserMonthlyStats")
    class GetUserMonthlyStats {

        @Test
        @DisplayName("사용자의 월별 평가 통계를 반환한다")
        void givenEvaluations_whenGetMonthlyStats_thenReturnsStats() {
            // given
            evaluationRepository.save(Evaluation.of(call1, evaluator, evaluated, FeedbackType.POSITIVE));
            evaluationRepository.save(Evaluation.of(call2, thirdUser, evaluated, FeedbackType.NEGATIVE));

            LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);

            // when
            List<Object[]> result = evaluationRepository.getUserMonthlyStats(evaluated, monthStart, monthEnd);

            // then
            assertThat(result).hasSize(2); // POSITIVE, NEGATIVE
        }
    }
}
