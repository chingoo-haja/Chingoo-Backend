package com.ldsilver.chingoohaja.dto.matching.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.validation.CommonValidationConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MatchingRequest(
        @NotNull(message = "카테고리 ID는 필수입니다.")
        @Min(value = CommonValidationConstants.Id.MIN_VALUE, message = CommonValidationConstants.Id.INVALID_ID)
        @JsonProperty("category_id") Long categoryId
) {
    public static MatchingRequest of(Long categoryId) {
        return new MatchingRequest(categoryId);
    }
}
