package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.admin.response.AdminStatisticsResponse;
import com.ldsilver.chingoohaja.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatisticsService {

    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final MatchingQueueRepository matchingQueueRepository;
    private final EvaluationRepository evaluationRepository;
    private final ReportRepository reportRepository;

    /**
     * 전체 시스템 통계 요약
     */
    public AdminStatisticsResponse.OverviewStats getOverviewStatistics() {
        log.debug("전체 시스템 통계 요약 조회 시작");

        long totalUsers = userRepository.count();
        long totalCalls = callRepository.countCompletedCallsBetween(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.now(),
                CallStatus.COMPLETED
        );
        long totalEvaluations = evaluationRepository.countEvaluationsBetween(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.now()
        );
        long totalReports = reportRepository.count();

        // 전체 매칭 성공률
        double overallMatchingSuccessRate = calculateMatchingSuccessRate(
                LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now()
        );

        // 전체 긍정 평가 비율
        double overallPositiveRate = calculatePositiveEvaluationRate(
                LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now()
        );

        // 평균 통화 시간
        Double avgDuration = callRepository.getAverageDurationMinutesBetween(
                LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now()
        );
        double averageCallDurationMinutes = avgDuration != null ?
                Math.round(avgDuration * 10.0) / 10.0 : 0.0;

        // 사용자당 평균 통화 수
        Double avgCallsPerUser = callRepository.getAverageCallsPerUser(
                LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now()
        );
        double averageCallsPerUser = avgCallsPerUser != null ?
                Math.round(avgCallsPerUser * 10.0) / 10.0 : 0.0;

        // 최근 30일 활성 사용자
        long activeUsersLast30Days = userRepository.countActiveUsers(
                LocalDateTime.now().minusDays(30)
        );

        log.info("전체 시스템 통계 요약 조회 완료 - 사용자: {}, 통화: {}, 평가: {}", totalUsers, totalCalls, totalEvaluations);

        return new AdminStatisticsResponse.OverviewStats(
                totalUsers, totalCalls, totalEvaluations, totalReports,
                overallMatchingSuccessRate, overallPositiveRate,
                averageCallDurationMinutes, averageCallsPerUser,
                activeUsersLast30Days, LocalDateTime.now()
        );
    }

    /**
     * 사용자 관련 누적 통계
     */
    public AdminStatisticsResponse.UserStats getUserStatistics() {
        log.debug("사용자 통계 조회 시작");

        long totalUsers = userRepository.count();
        long activeUsersLast30Days = userRepository.countActiveUsers(
                LocalDateTime.now().minusDays(30)
        );

        // 프로바이더별 분포
        List<Object[]> providerData = userRepository.getProviderStatistics();
        List<AdminStatisticsResponse.ProviderInfo> providerDistribution = new ArrayList<>();
        for (Object[] row : providerData) {
            String provider = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double percentage = totalUsers > 0 ?
                    Math.round((double) count / totalUsers * 1000.0) / 10.0 : 0.0;
            providerDistribution.add(new AdminStatisticsResponse.ProviderInfo(provider, count, percentage));
        }

        // 월별 가입 추이 (최근 12개월)
        List<AdminStatisticsResponse.MonthlySignup> monthlySignups = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDateTime monthStart = month.atDay(1).atStartOfDay();
            LocalDateTime monthEnd = month.atEndOfMonth().atTime(23, 59, 59);
            long count = userRepository.countUsersCreatedBetween(monthStart, monthEnd);
            monthlySignups.add(new AdminStatisticsResponse.MonthlySignup(
                    month.format(DateTimeFormatter.ofPattern("yyyy-MM")), count
            ));
        }

        // UserType별 분포
        List<AdminStatisticsResponse.UserTypeInfo> userTypeDistribution = new ArrayList<>();
        for (UserType type : UserType.values()) {
            long count = userRepository.findBySearchAndUserType(null, type,
                    org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
            userTypeDistribution.add(new AdminStatisticsResponse.UserTypeInfo(type.name(), count));
        }

        log.info("사용자 통계 조회 완료 - 총: {}, 활성: {}", totalUsers, activeUsersLast30Days);

        return new AdminStatisticsResponse.UserStats(
                totalUsers, activeUsersLast30Days,
                providerDistribution, monthlySignups, userTypeDistribution,
                LocalDateTime.now()
        );
    }

    /**
     * 통화 관련 통계 (기간별)
     */
    public AdminStatisticsResponse.CallStats getCallStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        log.debug("통화 통계 조회 - 기간: {} ~ {}", startDate, endDate);

        long totalCalls = callRepository.countCompletedCallsBetween(start, end, CallStatus.COMPLETED);

        Double avgDuration = callRepository.getAverageDurationMinutesBetween(start, end);
        double averageDurationMinutes = avgDuration != null ?
                Math.round(avgDuration * 10.0) / 10.0 : 0.0;

        Double avgCallsPerUser = callRepository.getAverageCallsPerUser(start, end);
        double averageCallsPerUser = avgCallsPerUser != null ?
                Math.round(avgCallsPerUser * 10.0) / 10.0 : 0.0;

        // 카테고리별 통화 통계
        List<Object[]> categoryData = callRepository.getCallStatsByCategory(start, end);
        List<AdminStatisticsResponse.CategoryCallInfo> categoryStats = categoryData.stream()
                .map(row -> new AdminStatisticsResponse.CategoryCallInfo(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        0 // getCallStatsByCategory에는 평균 시간 없음
                ))
                .toList();

        // 일별 통화 추이
        List<Object[]> dailyData = callRepository.getDailyCallStats(start, end);
        List<AdminStatisticsResponse.DailyCallTrend> dailyTrends = dailyData.stream()
                .map(row -> {
                    String date;
                    if (row[0] instanceof Date sqlDate) {
                        date = sqlDate.toLocalDate().toString();
                    } else {
                        date = row[0].toString();
                    }
                    long callCount = ((Number) row[1]).longValue();
                    double avgDur = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                    return new AdminStatisticsResponse.DailyCallTrend(
                            date, callCount, Math.round(avgDur * 10.0) / 10.0
                    );
                })
                .toList();

        // 시간대별 통화 분포
        List<Object[]> hourlyData = callRepository.getCallCountByHour(start, end);
        List<AdminStatisticsResponse.HourlyCallInfo> hourlyDistribution = hourlyData.stream()
                .map(row -> new AdminStatisticsResponse.HourlyCallInfo(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue()
                ))
                .toList();

        log.info("통화 통계 조회 완료 - 총: {}, 평균: {}분", totalCalls, averageDurationMinutes);

        return new AdminStatisticsResponse.CallStats(
                totalCalls, averageDurationMinutes, averageCallsPerUser,
                categoryStats, dailyTrends, hourlyDistribution,
                new AdminStatisticsResponse.Period(startDate, endDate)
        );
    }

    /**
     * 매칭 관련 통계 (기간별)
     */
    public AdminStatisticsResponse.MatchingStats getMatchingStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        log.debug("매칭 통계 조회 - 기간: {} ~ {}", startDate, endDate);

        // 전체 매칭 성공률
        List<Object[]> successRateData = matchingQueueRepository.getMatchingSuccessRate(start, end);
        long totalMatched = 0;
        long totalAttempts = 0;
        double overallSuccessRate = 0.0;
        if (!successRateData.isEmpty()) {
            Object[] data = successRateData.get(0);
            totalMatched = ((Number) data[0]).longValue();
            totalAttempts = ((Number) data[1]).longValue();
            overallSuccessRate = totalAttempts > 0 ?
                    Math.round((double) totalMatched / totalAttempts * 1000.0) / 10.0 : 0.0;
        }

        // 일별 매칭 성공률 추이
        List<Object[]> dailyData = matchingQueueRepository.getDailyMatchingSuccessRates(start, end);
        List<AdminStatisticsResponse.DailyMatchingTrend> dailyTrends = dailyData.stream()
                .map(row -> {
                    String date;
                    if (row[0] instanceof Date sqlDate) {
                        date = sqlDate.toLocalDate().toString();
                    } else {
                        date = row[0].toString();
                    }
                    long matched = ((Number) row[1]).longValue();
                    long total = ((Number) row[2]).longValue();
                    double rate = total > 0 ?
                            Math.round((double) matched / total * 1000.0) / 10.0 : 0.0;
                    return new AdminStatisticsResponse.DailyMatchingTrend(date, rate, matched, total);
                })
                .toList();

        // 시간대별 매칭 성공률
        List<Object[]> hourlyData = matchingQueueRepository.getHourlyMatchingSuccessRates(start, end);
        List<AdminStatisticsResponse.HourlyMatchingTrend> hourlyTrends = hourlyData.stream()
                .map(row -> {
                    int hour = ((Number) row[0]).intValue();
                    long matched = ((Number) row[1]).longValue();
                    long total = ((Number) row[2]).longValue();
                    double rate = total > 0 ?
                            Math.round((double) matched / total * 1000.0) / 10.0 : 0.0;
                    return new AdminStatisticsResponse.HourlyMatchingTrend(hour, rate, matched, total);
                })
                .toList();

        log.info("매칭 통계 조회 완료 - 성공률: {}%, 시도: {}", overallSuccessRate, totalAttempts);

        return new AdminStatisticsResponse.MatchingStats(
                overallSuccessRate, totalAttempts, totalMatched,
                dailyTrends, hourlyTrends,
                new AdminStatisticsResponse.Period(startDate, endDate)
        );
    }

    /**
     * 평가 관련 통계 (기간별)
     */
    public AdminStatisticsResponse.EvaluationStats getEvaluationStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        log.debug("평가 통계 조회 - 기간: {} ~ {}", startDate, endDate);

        long totalEvaluations = evaluationRepository.countEvaluationsBetween(start, end);

        // 피드백 타입별 분포
        List<Object[]> feedbackData = evaluationRepository.countByFeedbackTypeBetween(start, end);
        long positive = 0, neutral = 0, negative = 0;
        for (Object[] row : feedbackData) {
            String feedbackType = row[0] != null ? row[0].toString() : null;
            long count = ((Number) row[1]).longValue();
            if (feedbackType == null) continue;
            switch (feedbackType) {
                case "POSITIVE" -> positive = count;
                case "NEUTRAL" -> neutral = count;
                case "NEGATIVE" -> negative = count;
            }
        }

        double positiveRate = totalEvaluations > 0 ?
                Math.round((double) positive / totalEvaluations * 1000.0) / 10.0 : 0.0;
        double negativeRate = totalEvaluations > 0 ?
                Math.round((double) negative / totalEvaluations * 1000.0) / 10.0 : 0.0;

        AdminStatisticsResponse.FeedbackDistribution feedbackDistribution =
                new AdminStatisticsResponse.FeedbackDistribution(
                        positive, neutral, negative, positiveRate, negativeRate
                );

        log.info("평가 통계 조회 완료 - 총: {}, 긍정: {}%", totalEvaluations, positiveRate);

        return new AdminStatisticsResponse.EvaluationStats(
                totalEvaluations, feedbackDistribution,
                new AdminStatisticsResponse.Period(startDate, endDate)
        );
    }

    // ===== Private Helper Methods =====

    private double calculateMatchingSuccessRate(LocalDateTime start, LocalDateTime end) {
        List<Object[]> data = matchingQueueRepository.getMatchingSuccessRate(start, end);
        if (data.isEmpty()) return 0.0;
        Object[] row = data.get(0);
        long matched = ((Number) row[0]).longValue();
        long total = ((Number) row[1]).longValue();
        return total > 0 ? Math.round((double) matched / total * 1000.0) / 10.0 : 0.0;
    }

    private double calculatePositiveEvaluationRate(LocalDateTime start, LocalDateTime end) {
        List<Object[]> feedbackData = evaluationRepository.countByFeedbackTypeBetween(start, end);
        long positive = 0, total = 0;
        for (Object[] row : feedbackData) {
            String feedbackType = row[0] != null ? row[0].toString() : null;
            long count = ((Number) row[1]).longValue();
            total += count;
            if ("POSITIVE".equals(feedbackType)) {
                positive = count;
            }
        }
        return total > 0 ? Math.round((double) positive / total * 1000.0) / 10.0 : 0.0;
    }
}
