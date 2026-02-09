package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record UserMatchingHistoryResponse(
        @JsonProperty("user_info") UserInfo userInfo,
        @JsonProperty("matching_summary") MatchingSummary matchingSummary,
        @JsonProperty("matching_history") List<MatchingRecord> matchingHistory,
        @JsonProperty("call_history") List<CallRecord> callHistory,
        @JsonProperty("pagination") Pagination pagination
) {
    public record UserInfo(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("email") String email
    ) {}

    public record MatchingSummary(
            @JsonProperty("total_matching_attempts") long totalMatchingAttempts,
            @JsonProperty("successful_matches") long successfulMatches,
            @JsonProperty("cancelled_matches") long cancelledMatches,
            @JsonProperty("expired_matches") long expiredMatches,
            @JsonProperty("total_completed_calls") long totalCompletedCalls,
            @JsonProperty("total_call_duration_minutes") long totalCallDurationMinutes
    ) {}

    public record MatchingRecord(
            @JsonProperty("matching_id") Long matchingId,
            @JsonProperty("queue_id") String queueId,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("queue_type") String queueType,
            @JsonProperty("queue_status") String queueStatus,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}

    public record CallRecord(
            @JsonProperty("call_id") Long callId,
            @JsonProperty("partner_id") Long partnerId,
            @JsonProperty("partner_nickname") String partnerNickname,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("call_status") String callStatus,
            @JsonProperty("started_at") LocalDateTime startedAt,
            @JsonProperty("ended_at") LocalDateTime endedAt,
            @JsonProperty("duration_minutes") int durationMinutes
    ) {}

    public record Pagination(
            @JsonProperty("current_page") int currentPage,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_count") long totalCount,
            @JsonProperty("has_next") boolean hasNext
    ) {}
}
