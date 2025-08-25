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
        @JsonProperty("estimated_wait_time") Integer estimatedWaitTime,
        @JsonProperty("queue_position") Integer queuePosition,
        @JsonProperty("waiting_count") Long waitingCount,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static MatchingStatusResponse notInQueue() {
        return new MatchingStatusResponse(
                false, null, null, null,
                null, null, null, null, LocalDateTime.now()
        );
    }

    public static MatchingStatusResponse inQueue(
            String queueId,
            Long categoryId,
            String categoryName,
            QueueStatus queueStatus,
            Integer estimatedWaitTimeSeconds,
            Integer queuePosition,
            Long waitingCount) {
        return new MatchingStatusResponse(
                true,
                queueId,
                categoryId,
                categoryName,
                queueStatus,
                estimatedWaitTimeSeconds,
                queuePosition,
                waitingCount,
                LocalDateTime.now()
        );
    }
}
