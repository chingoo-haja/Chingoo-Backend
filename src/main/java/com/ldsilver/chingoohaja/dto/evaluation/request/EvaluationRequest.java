package com.ldsilver.chingoohaja.dto.evaluation.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.evaluation.enums.FeedbackType;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;

public record EvaluationRequest(
        @NotNull(message = "통화 ID는 필수입니다.")
        @Min(value = CommonValidationConstants.Id.MIN_VALUE, message = CommonValidationConstants.Id.INVALID_ID)
        @JsonProperty("call_id")
        Long callId,

        @NotNull(message = "피드백 타입은 필수입니다.")
        @JsonProperty("feedback_type")
        FeedbackType feedbackType
) {
    public static EvaluationRequest positive(Long callId) {
        return new EvaluationRequest(callId, FeedbackType.POSITIVE);
    }

    public static EvaluationRequest negative(Long callId) {
        return new EvaluationRequest(callId, FeedbackType.NEGATIVE);
    }

    public static EvaluationRequest of(Long callId, String feedbackType) {
        if (feedbackType == null) {
            throw new CustomException(ErrorCode.FEEDBACK_TYPE_NOT_NULL);
        }
        FeedbackType type = Arrays.stream(FeedbackType.values())
                .filter(ft -> ft.name().equalsIgnoreCase(feedbackType))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_FEEDBACK_TYPE));
        return new EvaluationRequest(callId, type);
    }

    public boolean isPositive() {
        return feedbackType == FeedbackType.POSITIVE;
    }

    public boolean isNegative() {
        return feedbackType == FeedbackType.NEGATIVE;
    }
}
