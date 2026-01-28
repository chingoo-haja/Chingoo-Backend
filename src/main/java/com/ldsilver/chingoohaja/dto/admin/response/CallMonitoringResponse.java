package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record CallMonitoringResponse(
        @JsonProperty("active_calls") List<ActiveCallInfo> activeCalls,
        @JsonProperty("recent_ended_calls") List<EndedCallInfo> recentEndedCalls,
        @JsonProperty("statistics") CallStatistics statistics
) {
    public record ActiveCallInfo(
            @JsonProperty("call_id") Long callId,
            @JsonProperty("user1_nickname") String user1Nickname,
            @JsonProperty("user2_nickname") String user2Nickname,
            @JsonProperty("category") String category,
            @JsonProperty("started_at") LocalDateTime startedAt,
            @JsonProperty("duration_minutes") int durationMinutes,
            @JsonProperty("recording_status") String recordingStatus
    ) {}

    public record EndedCallInfo(
            @JsonProperty("call_id") Long callId,
            @JsonProperty("category") String category,
            @JsonProperty("started_at") LocalDateTime startedAt,
            @JsonProperty("ended_at") LocalDateTime endedAt,
            @JsonProperty("duration_minutes") int durationMinutes,
            @JsonProperty("had_evaluation") boolean hadEvaluation
    ) {}

    public record CallStatistics(
            @JsonProperty("total_today") long totalToday,
            @JsonProperty("average_duration_minutes") double averageDurationMinutes,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("peak_hour") int peakHour
    ) {}
}