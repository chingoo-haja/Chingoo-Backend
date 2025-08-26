package com.ldsilver.chingoohaja.dto.matching.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MatchingCancelRequest(
        @NotBlank(message = "큐 ID는 필수입니다.")
        @Size(min = 10, max = 100, message = "올바르지 않은 큐 ID 형식입니다.")
        @JsonProperty("queue_id") String queueId
) {
    public static MatchingCancelRequest of(String queueId) {
        return new MatchingCancelRequest(queueId);
    }
}
