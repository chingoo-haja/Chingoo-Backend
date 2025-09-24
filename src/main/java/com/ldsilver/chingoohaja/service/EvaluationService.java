package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.evaluation.request.EvaluationRequest;
import com.ldsilver.chingoohaja.dto.evaluation.response.EvaluationResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.EvaluationRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Evaluation evaluation = Evaluation.of(call, evaluator, evaluated, request.feedbackType());
        Evaluation savedEvaluation = evaluationRepository.save(evaluation);

        log.info("평가 제출 완료 - evaluationId: {}, evaluator: {}, evaluated: {}, type: {}",
                savedEvaluation.getId(), evaluatorId, evaluated.getId(), request.feedbackType());

        return EvaluationResponse.from(savedEvaluation);
    }

    private boolean hasAlreadyEvaluated(Call call, User evaluator, User evaluated) {
        return evaluationRepository.findByCallAndEvaluatorAndEvaluated(call, evaluator, evaluated)
                .isPresent();
    }
}
