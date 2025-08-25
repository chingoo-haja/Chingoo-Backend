package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;

import java.time.LocalDateTime;

public record MatchingStatusResponse(
        @JsonProperty("is_in_queue") boolean isInQueue,
        @JsonProperty("queue_id") String queueId,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("queue_status") QueueStatus queueStatus,
        @JsonProperty("estimated_wait_time_seconds") Integer estimatedWaitTimeSeconds,
        @JsonProperty("queue_position") Integer queuePosition,
        @JsonProperty("joined_at") LocalDateTime joinedAt,
        @JsonProperty("elapsed_time_seconds") Long elapsedTimeSeconds
) {
    public static MatchingStatusResponse notInQueue() {
        return new MatchingStatusResponse(
                false, null, null, null, null, null, null, null, null
        );
    }

    public static MatchingStatusResponse inQueue(
            String queueId,
            Long categoryId,
            String categoryName,
            QueueStatus queueStatus,
            Integer estimatedWaitTimeSeconds,
            Integer queuePosition,
            LocalDateTime joinedAt,
            Long elapsedTimeSeconds) {
        return new MatchingStatusResponse(
                true,
                queueId,
                categoryId,
                categoryName,
                queueStatus,
                estimatedWaitTimeSeconds,
                queuePosition,
                joinedAt,
                elapsedTimeSeconds
        );
    }
}
