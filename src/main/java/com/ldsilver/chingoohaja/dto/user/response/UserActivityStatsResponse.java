package com.ldsilver.chingoohaja.dto.user.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record UserActivityStatsResponse(
        @JsonProperty("weekly_stats") WeeklyStats weeklyStats,
        @JsonProperty("quarterly_stats") QuarterlyStats quarterlyStats,
        @JsonProperty("additional_stats") AdditionalStats additionalStats
) {
    public record WeeklyStats(
            @JsonProperty("call_count") int callCount,
            @JsonProperty("total_duration_minutes") int totalDurationMinutes,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate
    ) {}

    public record QuarterlyStats(
            @JsonProperty("call_count") int callCount,
            @JsonProperty("total_duration_minutes") int totalDurationMinutes,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("quarter") int quarter
    ) {}

    public record AdditionalStats(
            @JsonProperty("average_call_duration_minutes") int averageCallDurationMinutes,
            @JsonProperty("most_used_category") MostUsedCategory mostUsedCategory,
            @JsonProperty("total_data_usage_mb") double totalDataUsageMB,
            @JsonProperty("average_network_quality") double averageNetworkQuality
    ) {}

    public record MostUsedCategory(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name
    ) {
        public static MostUsedCategory empty() {
            return new MostUsedCategory(null, null);
        }
    }

    public static UserActivityStatsResponse of(
            WeeklyStats weeklyStats,
            QuarterlyStats quarterlyStats,
            AdditionalStats additionalStats
    ) {
        return new UserActivityStatsResponse(weeklyStats, quarterlyStats, additionalStats);
    }
}