package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record RealtimeMatchingStatsResponse(
        @JsonProperty("total_waiting_users") long totalWaitingUsers,
        @JsonProperty("active_categories") int activeCategories,
        @JsonProperty("average_wait_time_seconds") double averageWaitTimeSeconds,
        @JsonProperty("matching_success_rate") double matchingSuccessRate,
        @JsonProperty("peak_hours") List<PeakHour> peakHours,
        @JsonProperty("category_stats") List<CategoryRealTimeStats> categoryStats,
        @JsonProperty("server_performance") ServerPerformance serverPerformance,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static RealtimeMatchingStatsResponse of(
            long totalWaiting,
            int activeCategories,
            double avgWaitTime,
            double successRate,
            List<PeakHour> peakHours,
            List<CategoryRealTimeStats> categoryStats,
            ServerPerformance serverPerformance) {
        return new RealtimeMatchingStatsResponse(
                totalWaiting,
                activeCategories,
                avgWaitTime,
                successRate,
                peakHours,
                categoryStats,
                serverPerformance,
                LocalDateTime.now()
        );
    }

    public record PeakHour(
            @JsonProperty("hour") int hour,
            @JsonProperty("usage_percentage") double usagePercentage,
            @JsonProperty("success_rate") double successRate
    ) {}

    public record CategoryRealTimeStats(
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("waiting_count") long waitingCount,
            @JsonProperty("estimated_wait_time") int estimatedWaitTime,
            @JsonProperty("success_rate_today") double successRateToday,
            @JsonProperty("popularity_rank") int popularityRank,
            @JsonProperty("trend") String trend // UP, DOWN, STABLE
    ) {}

    public record ServerPerformance(
            @JsonProperty("redis_status") String redisStatus,
            @JsonProperty("average_response_time_ms") double averageResponseTimeMs,
            @JsonProperty("active_connections") int activeConnections,
            @JsonProperty("queue_health") String queueHealth
    ) {
        public static ServerPerformance healthy() {
            return new ServerPerformance("HEALTHY", 15.5, 0, "OPTIMAL");
        }
    }
}
