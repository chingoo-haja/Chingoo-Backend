package com.ldsilver.chingoohaja.dto.evaluation.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record EvaluationStatsResponse(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("total_evaluations") long totalEvaluations,
        @JsonProperty("positive_evaluations") long positiveEvaluations,
        @JsonProperty("negative_evaluations") long negativeEvaluations,
        @JsonProperty("positive_rate") double positiveRate,
        @JsonProperty("ranking_percentile") Double rankingPercentile,
        @JsonProperty("is_top_10_percent") boolean isTop10Percent,
        @JsonProperty("calculated_at") LocalDateTime calculatedAt
) {
    public static EvaluationStatsResponse of(
            Long userId,
            long totalEvaluations,
            long positiveEvaluations,
            long negativeEvaluations,
            Double rankingPercentile) {

        double positiveRate = totalEvaluations > 0 ? (double) positiveEvaluations / totalEvaluations * 100 : 0.0;
        boolean isTop10Percent = rankingPercentile != null && rankingPercentile >= 90.0;

        return new EvaluationStatsResponse(
                userId,
                totalEvaluations,
                positiveEvaluations,
                negativeEvaluations,
                positiveRate,
                rankingPercentile,
                isTop10Percent,
                LocalDateTime.now()
        );
    }

    public static EvaluationStatsResponse noEvaluations(Long userId) {
        return new EvaluationStatsResponse(
                userId, 0L, 0L, 0L, 0.0, null, false, LocalDateTime.now()
        );
    }

    public boolean hasEvaluations() {
        return totalEvaluations > 0;
    }

    public String getPerformanceGrade() {
        if (!hasEvaluations()) return "신규";
        if (isTop10Percent) return "우수";
        if (positiveRate >= 80.0) return "양호";
        if (positiveRate >= 60.0) return "보통";
        return "개선필요";
    }
}
