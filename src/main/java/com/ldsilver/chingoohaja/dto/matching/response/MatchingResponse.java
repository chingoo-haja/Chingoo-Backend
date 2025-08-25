package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;

import java.time.LocalDateTime;

public record MatchingResponse(
        @JsonProperty("queue_id") String queueId,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("queue_status") QueueStatus queueStatus,
        @JsonProperty("estimated_wait_time_seconds") Integer estimatedWaitTimeSeconds,
        @JsonProperty("queue_position") Integer queuePosition,
        @JsonProperty("joined_at") LocalDateTime joinedAt
) {
    public static MatchingResponse of(
            String queueId,
            Long categoryId,
            String categoryName,
            QueueStatus queueStatus,
            Integer estimatedWaitTimeSeconds,
            Integer queuePosition,
            LocalDateTime joinedAt) {
        return new MatchingResponse(
                queueId,
                categoryId,
                categoryName,
                queueStatus,
                estimatedWaitTimeSeconds,
                queuePosition,
                joinedAt
        );
    }

    public static MatchingResponse waiting(
            String queueId,
            Long categoryId,
            String categoryName,
            Integer estimatedWaitTimeSeconds,
            Integer queuePosition) {
        return new MatchingResponse(
                queueId,
                categoryId,
                categoryName,
                QueueStatus.WAITING,
                estimatedWaitTimeSeconds,
                queuePosition,
                LocalDateTime.now()
        );
    }

    public boolean isWaiting() {
        return queueStatus == QueueStatus.WAITING;
    }

    public boolean isMatching() {
        return queueStatus == QueueStatus.MATCHING;
    }
}
