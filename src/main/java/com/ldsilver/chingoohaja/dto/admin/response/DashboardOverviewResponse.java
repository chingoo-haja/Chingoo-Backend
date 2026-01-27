package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardOverviewResponse(
        @JsonProperty("system_health") SystemHealth systemHealth,
        @JsonProperty("real_time_stats") RealTimeStats realTimeStats,
        @JsonProperty("today_summary") TodaySummary todaySummary,
        @JsonProperty("recent_alerts") List<Alert> recentAlerts,
        @JsonProperty("timestamp") LocalDateTime timestamp
) {
    public record SystemHealth(
            @JsonProperty("database_status") String databaseStatus,
            @JsonProperty("redis_status") String redisStatus,
            @JsonProperty("agora_status") String agoraStatus,
            @JsonProperty("overall_status") String overallStatus
    ) {}

    public record RealTimeStats(
            @JsonProperty("active_calls") int activeCalls,
            @JsonProperty("users_in_queue") int usersInQueue,
            @JsonProperty("active_users_now") int activeUsersNow,
            @JsonProperty("recordings_in_progress") int recordingsInProgress
    ) {}

    public record TodaySummary(
            @JsonProperty("total_calls") long totalCalls,
            @JsonProperty("new_users") long newUsers,
            @JsonProperty("reports_count") long reportsCount,
            @JsonProperty("success_rate") double successRate
    ) {}

    public record Alert(
            @JsonProperty("level") String level, // INFO, WARNING, ERROR
            @JsonProperty("message") String message,
            @JsonProperty("timestamp") LocalDateTime timestamp
    ) {}
}