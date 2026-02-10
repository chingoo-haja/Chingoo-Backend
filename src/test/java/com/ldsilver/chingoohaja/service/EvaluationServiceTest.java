package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.call.enums.CallType;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.evaluation.request.EvaluationRequest;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationResponse;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationStatsResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.EvaluationRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationService 테스트")
class EvaluationServiceTest {

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private CallRepository callRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private EvaluationService evaluationService;

    private User evaluator;
    private User evaluated;
    private Call completedCall;
    private Category category;

    @BeforeEach
    void setUp() {
        evaluator = User.of("eval1@test.com", "평가자", "평가자실명", Gender.MALE, LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k1");
        evaluated = User.of("eval2@test.com", "피평가자", "피평가자실명", Gender.FEMALE, LocalDate.of(1992, 5, 15), null, UserType.USER, null, "kakao", "k2");
        setId(evaluator, 1L);
        setId(evaluated, 2L);

        category = createCategory(1L, "일상");
        completedCall = createCompletedCall();
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

    private Call createCompletedCall() {
        Call call = Call.from(evaluator, evaluated, category, CallType.RANDOM_MATCH);
        setId(call, 100L);
        call.startCall();
        call.endCall();
        return call;
    }

    @Nested
    @DisplayName("submitEvaluation")
    class SubmitEvaluation {

        @Test
        @DisplayName("완료된 통화에 대해 긍정 평가를 제출한다")
        void givenCompletedCall_whenSubmitPositive_thenSavesEvaluation() {
            // given
            EvaluationRequest request = EvaluationRequest.positive(100L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));
            when(evaluationRepository.findByCallAndEvaluatorAndEvaluated(completedCall, evaluator, evaluated))
                    .thenReturn(Optional.empty());
            when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(invocation -> {
                Evaluation eval = invocation.getArgument(0);
                setId(eval, 50L);
                return eval;
            });

            // when
            EvaluationResponse response = evaluationService.submitEvaluation(1L, request);

            // then
            assertThat(response).isNotNull();
            verify(evaluationRepository).save(any(Evaluation.class));
        }

        @Test
        @DisplayName("evaluatorId가 null이면 예외를 던진다")
        void givenNullEvaluatorId_whenSubmit_thenThrowsException() {
            // given
            EvaluationRequest request = EvaluationRequest.positive(100L);

            // when & then
            assertThatThrownBy(() -> evaluationService.submitEvaluation(null, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @Test
        @DisplayName("통화 참가자가 아니면 평가할 수 없다")
        void givenNonParticipant_whenSubmit_thenThrowsException() {
            // given
            User outsider = User.of("out@test.com", "외부인", "외부인실명", Gender.MALE, LocalDate.of(1995, 1, 1), null, UserType.USER, null, "kakao", "k3");
            setId(outsider, 999L);

            EvaluationRequest request = EvaluationRequest.positive(100L);

            when(userRepository.findById(999L)).thenReturn(Optional.of(outsider));
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));

            // when & then
            assertThatThrownBy(() -> evaluationService.submitEvaluation(999L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EVALUATION_NOT_ALLOWED));
        }

        @Test
        @DisplayName("완료되지 않은 통화는 평가할 수 없다")
        void givenInProgressCall_whenSubmit_thenThrowsException() {
            // given
            Call inProgressCall = Call.from(evaluator, evaluated, category, CallType.RANDOM_MATCH);
            setId(inProgressCall, 101L);
            inProgressCall.startCall();

            EvaluationRequest request = EvaluationRequest.positive(101L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(callRepository.findById(101L)).thenReturn(Optional.of(inProgressCall));

            // when & then
            assertThatThrownBy(() -> evaluationService.submitEvaluation(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EVALUATION_FOR_NOT_COMPLETED_CALL));
        }

        @Test
        @DisplayName("이미 평가한 통화에 중복 평가할 수 없다")
        void givenAlreadyEvaluated_whenSubmit_thenThrowsException() {
            // given
            EvaluationRequest request = EvaluationRequest.positive(100L);
            Evaluation existing = Evaluation.of(completedCall, evaluator, evaluated, FeedbackType.POSITIVE);

            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));
            when(evaluationRepository.findByCallAndEvaluatorAndEvaluated(completedCall, evaluator, evaluated))
                    .thenReturn(Optional.of(existing));

            // when & then
            assertThatThrownBy(() -> evaluationService.submitEvaluation(1L, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EVALUATION_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("자기 자신을 평가할 수 없다")
        void givenSelfEvaluation_whenSubmit_thenThrowsException() {
            // given
            // evaluator가 자기 자신을 평가하려는 상황
            // Call.from()은 동일 사용자를 허용하지 않으므로 별도 사용자로 Call을 생성 후 id를 조작
            User sameUser = User.of("same@test.com", "동일유저", "동일유저", Gender.MALE, LocalDate.of(1990, 1, 1), null, UserType.USER, null, "kakao", "k_same");
            setId(sameUser, 1L);

            // 직접 Call을 생성하지 않고, completedCall에서 getPartner가 같은 id를 반환하도록
            // 별도의 Call 없이, evaluated의 id를 evaluator와 동일하게 조작
            Call trickCall = Call.from(evaluator, evaluated, category, CallType.RANDOM_MATCH);
            setId(trickCall, 102L);
            trickCall.startCall();
            trickCall.endCall();

            // evaluated(id=2)의 id를 1로 변경해서 자기평가 상황 연출
            setId(evaluated, 1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(callRepository.findById(102L)).thenReturn(Optional.of(trickCall));

            EvaluationRequest request2 = EvaluationRequest.positive(102L);

            // when & then
            assertThatThrownBy(() -> evaluationService.submitEvaluation(1L, request2))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SELF_EVALUATION_NOT_ALLOWED));

            // restore
            setId(evaluated, 2L);
        }
    }

    @Nested
    @DisplayName("getUserEvaluationStats")
    class GetUserEvaluationStats {

        @Test
        @DisplayName("사용자의 평가 통계를 조회한다")
        void givenUserWithEvaluations_whenGetStats_thenReturnsStats() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));

            Object[] positiveStat = new Object[]{FeedbackType.POSITIVE, 8L};
            Object[] negativeStat = new Object[]{FeedbackType.NEGATIVE, 2L};
            when(evaluationRepository.getUserMonthlyStats(eq(evaluator), any(), any()))
                    .thenReturn(List.of(positiveStat, negativeStat));
            when(evaluationRepository.getPositiveFeedbackPercentageByUser(evaluator))
                    .thenReturn(80.0);
            when(evaluationRepository.getAllUsersPositiveRates())
                    .thenReturn(List.of(
                            new Object[]{1L, 80.0},
                            new Object[]{2L, 60.0},
                            new Object[]{3L, 90.0}
                    ));

            // when
            EvaluationStatsResponse response = evaluationService.getUserEvaluationStats(1L);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("평가 이력이 없으면 기본 응답을 반환한다")
        void givenNoEvaluations_whenGetStats_thenReturnsNoEvaluations() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(evaluationRepository.getUserMonthlyStats(eq(evaluator), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(evaluationRepository.getPositiveFeedbackPercentageByUser(evaluator))
                    .thenReturn(null);

            // when
            EvaluationStatsResponse response = evaluationService.getUserEvaluationStats(1L);

            // then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("canEvaluate")
    class CanEvaluate {

        @Test
        @DisplayName("평가 가능한 조건이면 true를 반환한다")
        void givenEligibleConditions_whenCheck_thenReturnsTrue() {
            // given
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));
            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(evaluationRepository.findByCallAndEvaluatorAndEvaluated(completedCall, evaluator, evaluated))
                    .thenReturn(Optional.empty());

            // when
            boolean result = evaluationService.canEvaluate(1L, 100L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("통화가 존재하지 않으면 false를 반환한다")
        void givenNonExistentCall_whenCheck_thenReturnsFalse() {
            // given
            when(callRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            boolean result = evaluationService.canEvaluate(1L, 999L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("통화 참가자가 아니면 false를 반환한다")
        void givenNonParticipant_whenCheck_thenReturnsFalse() {
            // given
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));

            // when
            boolean result = evaluationService.canEvaluate(999L, 100L);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이미 평가한 통화면 false를 반환한다")
        void givenAlreadyEvaluated_whenCheck_thenReturnsFalse() {
            // given
            Evaluation existing = Evaluation.of(completedCall, evaluator, evaluated, FeedbackType.POSITIVE);
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));
            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(evaluationRepository.findByCallAndEvaluatorAndEvaluated(completedCall, evaluator, evaluated))
                    .thenReturn(Optional.of(existing));

            // when
            boolean result = evaluationService.canEvaluate(1L, 100L);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("hasUserEvaluatedCall")
    class HasUserEvaluatedCall {

        @Test
        @DisplayName("평가한 이력이 있으면 true를 반환한다")
        void givenEvaluated_whenCheck_thenReturnsTrue() {
            // given
            Evaluation existing = Evaluation.of(completedCall, evaluator, evaluated, FeedbackType.POSITIVE);
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));
            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(evaluationRepository.findByCallAndEvaluatorAndEvaluated(completedCall, evaluator, evaluated))
                    .thenReturn(Optional.of(existing));

            // when
            boolean result = evaluationService.hasUserEvaluatedCall(1L, 100L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("평가한 이력이 없으면 false를 반환한다")
        void givenNotEvaluated_whenCheck_thenReturnsFalse() {
            // given
            when(callRepository.findById(100L)).thenReturn(Optional.of(completedCall));
            when(userRepository.findById(1L)).thenReturn(Optional.of(evaluator));
            when(evaluationRepository.findByCallAndEvaluatorAndEvaluated(completedCall, evaluator, evaluated))
                    .thenReturn(Optional.empty());

            // when
            boolean result = evaluationService.hasUserEvaluatedCall(1L, 100L);

            // then
            assertThat(result).isFalse();
        }
    }
}
