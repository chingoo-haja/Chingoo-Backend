package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.evaluation.request.EvaluationRequest;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationResponse;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationStatsResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.EvaluationRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final CallRepository callRepository;
    private final UserRepository userRepository;

    @Transactional
    public EvaluationResponse submitEvaluation(Long evaluatorId, EvaluationRequest request) {
        log.debug("평가 제출 시작 - evaluatorId: {}, callId: {}, feedbackType: {}",
                evaluatorId, request.callId(), request.feedbackType());

        User evaluator = userRepository.findById(evaluatorId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Call call = callRepository.findById(request.callId())
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(evaluatorId)) {
            throw new CustomException(ErrorCode.EVALUATION_NOT_ALLOWED);
        }

        if (call.getCallStatus() != CallStatus.COMPLETED) {
            throw new CustomException(ErrorCode.EVALUATION_FOR_NOT_COMPLETED_CALL);
        }

        User evaluated = call.getPartner(evaluatorId);

        if (evaluator.getId().equals(evaluated.getId())){
            throw new CustomException(ErrorCode.SELF_EVALUATION_NOT_ALLOWED);
        }

        if (hasAlreadyEvaluated(call, evaluator, evaluated)) {
            throw new CustomException(ErrorCode.EVALUATION_ALREADY_EXISTS);
        }

        try {
            Evaluation evaluation = Evaluation.of(call, evaluator, evaluated, request.feedbackType());
            Evaluation savedEvaluation = evaluationRepository.save(evaluation);

            log.info("평가 제출 완료 - evaluationId: {}, evaluator: {}, evaluated: {}, type: {}",
                    savedEvaluation.getId(), evaluatorId, evaluated.getId(), request.feedbackType());

            return EvaluationResponse.from(savedEvaluation);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.EVALUATION_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public EvaluationStatsResponse getUserEvaluationStats(Long userId) {
        log.debug("사용자 평가 통계 조회 - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime monthStart = LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = LocalDateTime.now().with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59);

        var monthlyStats = evaluationRepository.getUserMonthlyStats(user, monthStart, monthEnd);
        long positiveCount = 0;
        long negativeCount = 0;
        long totalCount = 0;

        for (Object[] stat : monthlyStats) {
            FeedbackType feedbackType = (FeedbackType) stat[0];
            long count = ((Number) stat[1]).longValue();

            if (feedbackType == FeedbackType.POSITIVE) {
                positiveCount = count;
            } else if (feedbackType == FeedbackType.NEGATIVE) {
                negativeCount = count;
            }
            totalCount += count;
        }

        Double overallPositiveRate = evaluationRepository.getPositiveFeedbackPercentageByUser(user);
        if (overallPositiveRate == null) {
            return EvaluationStatsResponse.noEvaluations(userId);
        }

        Double rankingPercentile = calculateUserRankingPercentile(userId, overallPositiveRate);

        return EvaluationStatsResponse.of(
                userId, totalCount, positiveCount, negativeCount, rankingPercentile
        );
    }

    @Transactional(readOnly = true)
    public boolean canEvaluate(Long userId, Long callId) {
        log.debug("평가 가능 여부 확인 - userId: {}, callId: {}", userId, callId);

        try {
            Call call = callRepository.findById(callId)
                    .orElse(null);

            if (call == null) {
                return false;
            }

            // 통화 참가자가 아니면 평가 불가
            if (!call.isParticipant(userId)) {
                return false;
            }

            // 완료된 통화가 아니면 평가 불가
            if (call.getCallStatus() != CallStatus.COMPLETED) {
                return false;
            }

            // 이미 평가했으면 평가 불가
            User evaluator = userRepository.findById(userId).orElse(null);
            User evaluated = call.getPartner(userId);

            if (evaluator == null || evaluated == null) {
                return false;
            }

            return !hasAlreadyEvaluated(call, evaluator, evaluated);

        } catch (Exception e) {
            log.warn("평가 가능 여부 확인 중 오류 - userId: {}, callId: {}", userId, callId, e);
            return false;
        }
    }




    private boolean hasAlreadyEvaluated(Call call, User evaluator, User evaluated) {
        return evaluationRepository.findByCallAndEvaluatorAndEvaluated(call, evaluator, evaluated)
                .isPresent();
    }

    private Double calculateUserRankingPercentile(Long userId, double userPositiveRate) {
        try {
            var allUserStats = evaluationRepository.getAllUsersPositiveRates();

            if (allUserStats.isEmpty()) {
                return null;
            }

            long lowerRatedUsers = allUserStats.stream()
                    .mapToLong(stats -> {
                        Double rate = (Double) stats[1];
                        return (rate != null && rate < userPositiveRate) ? 1 : 0;
                    })
                    .sum();

            return ((double) lowerRatedUsers / allUserStats.size()) * 100;

        } catch (Exception e) {
            log.warn("순위 백분율 계산 실패 - userId: {}", userId, e);
            return null;
        }
    }
}
