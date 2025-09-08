package com.ldsilver.chingoohaja.dto.matching.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MatchingStatsResponse(
        @JsonProperty("summary") StatsSummary summary,
        @JsonProperty("time_series") List<TimeSeriesData> timeSeries,
        @JsonProperty("category_breakdown") List<CategoryStatsDetail> categoryBreakdown,
        @JsonProperty("user_analytics") UserAnalytics userAnalytics,
        @JsonProperty("trends") TrendAnalysis trends,
        @JsonProperty("generated_at") LocalDateTime generatedAt,
        @JsonProperty("period_info") PeriodInfo periodInfo
) {
    public static MatchingStatsResponse of(
            StatsSummary summary,
            List<TimeSeriesData> timeSeries,
            List<CategoryStatsDetail> categoryBreakdown,
            UserAnalytics userAnalytics,
            TrendAnalysis trends,
            PeriodInfo periodInfo) {
        return new MatchingStatsResponse(
                summary,
                timeSeries,
                categoryBreakdown,
                userAnalytics,
                trends,
                LocalDateTime.now(),
                periodInfo
        );
    }

    public record StatsSummary(
            @JsonProperty("total_matches") long totalMatches,
            @JsonProperty("successful_matches") long successfulMatches,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("average_wait_time") double averageWaitTime,
            @JsonProperty("total_wait_time") long totalWaitTime,
            @JsonProperty("unique_users") long uniqueUsers,
            @JsonProperty("peak_concurrent_users") int peakConcurrentUsers,
            @JsonProperty("average_call_duration") double averageCallDuration
    ) {}

    public record TimeSeriesData(
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("matches_count") int matchesCount,
            @JsonProperty("waiting_users") int waitingUsers,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("average_wait_time") double averageWaitTime
    ) {}

    public record CategoryStatsDetail(
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("total_matches") long totalMatches,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("average_wait_time") double averageWaitTime,
            @JsonProperty("average_call_time") double averageCallTime,
            @JsonProperty("popularity_score") double popularityScore,
            @JsonProperty("peak_hours") List<Integer> peakHours,
            @JsonProperty("user_satisfaction") double userSatisfaction,
            @JsonProperty("growth_rate") double growthRate
    ) {}

    public record UserAnalytics(
            @JsonProperty("new_users") int newUsers,
            @JsonProperty("returning_users") int returningUsers,
            @JsonProperty("user_retention_rate") double userRetentionRate,
            @JsonProperty("average_sessions_per_user") double averageSessionsPerUser,
            @JsonProperty("top_user_segments") List<UserSegment> topUserSegments
    ) {}

    public record UserSegment(
            @JsonProperty("segment_name") String segmentName,
            @JsonProperty("user_count") int userCount,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("preferred_categories") List<String> preferredCategories
    ) {}

    public record TrendAnalysis(
            @JsonProperty("daily_trends") Map<String, Double> dailyTrends,
            @JsonProperty("hourly_trends") Map<Integer, Double> hourlyTrends,
            @JsonProperty("category_trends") Map<String, TrendData> categoryTrends,
            @JsonProperty("predictions") List<Prediction> predictions
    ) {}

    public record TrendData(
            @JsonProperty("current_value") double currentValue,
            @JsonProperty("previous_value") double previousValue,
            @JsonProperty("change_percentage") double changePercentage,
            @JsonProperty("trend_direction") String trendDirection
    ) {}

    public record Prediction(
            @JsonProperty("metric") String metric,
            @JsonProperty("predicted_value") double predictedValue,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("prediction_date") LocalDateTime predictionDate
    ) {}

    public record PeriodInfo(
            @JsonProperty("period_type") String periodType,
            @JsonProperty("start_date") LocalDateTime startDate,
            @JsonProperty("end_date") LocalDateTime endDate,
            @JsonProperty("total_days") int totalDays,
            @JsonProperty("data_completeness") double dataCompleteness
    ) {}
}
