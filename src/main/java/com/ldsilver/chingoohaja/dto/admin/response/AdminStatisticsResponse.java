package com.ldsilver.chingoohaja.dto.admin.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminStatisticsResponse {

    // ===== GET /overview =====
    public record OverviewStats(
            @JsonProperty("total_users") long totalUsers,
            @JsonProperty("total_calls") long totalCalls,
            @JsonProperty("total_evaluations") long totalEvaluations,
            @JsonProperty("total_reports") long totalReports,
            @JsonProperty("overall_matching_success_rate") double overallMatchingSuccessRate,
            @JsonProperty("overall_positive_evaluation_rate") double overallPositiveEvaluationRate,
            @JsonProperty("average_call_duration_minutes") double averageCallDurationMinutes,
            @JsonProperty("average_calls_per_user") double averageCallsPerUser,
            @JsonProperty("active_users_last_30_days") long activeUsersLast30Days,
            @JsonProperty("generated_at") LocalDateTime generatedAt
    ) {}

    // ===== GET /users =====
    public record UserStats(
            @JsonProperty("total_users") long totalUsers,
            @JsonProperty("active_users_last_30_days") long activeUsersLast30Days,
            @JsonProperty("provider_distribution") List<ProviderInfo> providerDistribution,
            @JsonProperty("monthly_signups") List<MonthlySignup> monthlySignups,
            @JsonProperty("user_type_distribution") List<UserTypeInfo> userTypeDistribution,
            @JsonProperty("generated_at") LocalDateTime generatedAt
    ) {}

    public record ProviderInfo(
            @JsonProperty("provider") String provider,
            @JsonProperty("count") long count,
            @JsonProperty("percentage") double percentage
    ) {}

    public record MonthlySignup(
            @JsonProperty("month") String month,
            @JsonProperty("count") long count
    ) {}

    public record UserTypeInfo(
            @JsonProperty("user_type") String userType,
            @JsonProperty("count") long count
    ) {}

    // ===== GET /calls =====
    public record CallStats(
            @JsonProperty("total_calls") long totalCalls,
            @JsonProperty("average_duration_minutes") double averageDurationMinutes,
            @JsonProperty("average_calls_per_user") double averageCallsPerUser,
            @JsonProperty("category_stats") List<CategoryCallInfo> categoryStats,
            @JsonProperty("daily_trends") List<DailyCallTrend> dailyTrends,
            @JsonProperty("hourly_distribution") List<HourlyCallInfo> hourlyDistribution,
            @JsonProperty("period") Period period
    ) {}

    public record CategoryCallInfo(
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("call_count") long callCount,
            @JsonProperty("average_duration_seconds") double averageDurationSeconds
    ) {}

    public record DailyCallTrend(
            @JsonProperty("date") String date,
            @JsonProperty("call_count") long callCount,
            @JsonProperty("average_duration_seconds") double averageDurationSeconds
    ) {}

    public record HourlyCallInfo(
            @JsonProperty("hour") int hour,
            @JsonProperty("call_count") long callCount
    ) {}

    // ===== GET /matching =====
    public record MatchingStats(
            @JsonProperty("overall_success_rate") double overallSuccessRate,
            @JsonProperty("total_matching_attempts") long totalMatchingAttempts,
            @JsonProperty("total_matched") long totalMatched,
            @JsonProperty("daily_trends") List<DailyMatchingTrend> dailyTrends,
            @JsonProperty("hourly_trends") List<HourlyMatchingTrend> hourlyTrends,
            @JsonProperty("period") Period period
    ) {}

    public record DailyMatchingTrend(
            @JsonProperty("date") String date,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("matched") long matched,
            @JsonProperty("total") long total
    ) {}

    public record HourlyMatchingTrend(
            @JsonProperty("hour") int hour,
            @JsonProperty("success_rate") double successRate,
            @JsonProperty("matched") long matched,
            @JsonProperty("total") long total
    ) {}

    // ===== GET /evaluations =====
    public record EvaluationStats(
            @JsonProperty("total_evaluations") long totalEvaluations,
            @JsonProperty("feedback_distribution") FeedbackDistribution feedbackDistribution,
            @JsonProperty("period") Period period
    ) {}

    public record FeedbackDistribution(
            @JsonProperty("positive") long positive,
            @JsonProperty("neutral") long neutral,
            @JsonProperty("negative") long negative,
            @JsonProperty("positive_rate") double positiveRate,
            @JsonProperty("negative_rate") double negativeRate
    ) {}

    // ===== 공통 =====
    public record Period(
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate
    ) {}
}
