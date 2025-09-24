package com.ldsilver.chingoohaja.dto.evaluation.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;

import java.time.LocalDateTime;

public record EvaluationResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("call_id") Long callId,
        @JsonProperty("evaluator_id") Long evaluatorId,
        @JsonProperty("evaluated_id") Long evaluatedId,
        @JsonProperty("evaluated_nickname") String evaluatedNickname,
        @JsonProperty("feedback_type") FeedbackType feedbackType,
        @JsonProperty("is_positive") boolean isPositive,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public static EvaluationResponse from(Evaluation evaluation) {
        return new EvaluationResponse(
                evaluation.getId(),
                evaluation.getCall().getId(),
                evaluation.getEvaluator().getId(),
                evaluation.getEvaluated().getId(),
                evaluation.getEvaluated().getNickname(),
                evaluation.getFeedbackType(),
                evaluation.getFeedbackType() == FeedbackType.POSITIVE,
                evaluation.getCreatedAt()
        );
    }

    public static EvaluationResponse submitted(Evaluation evaluation, String evaluatedNickname) {
        return new EvaluationResponse(
                evaluation.getId(),
                evaluation.getCall().getId(),
                evaluation.getEvaluator().getId(),
                evaluation.getEvaluated().getId(),
                evaluatedNickname,
                evaluation.getFeedbackType(),
                evaluation.getFeedbackType() == FeedbackType.POSITIVE,
                evaluation.getCreatedAt()
        );
    }
}
