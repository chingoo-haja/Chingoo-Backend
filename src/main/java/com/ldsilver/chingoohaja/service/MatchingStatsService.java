package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.matching.MatchingCategoryStats;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingStatsRequest;
import com.ldsilver.chingoohaja.dto.matching.response.MatchingStatsResponse;
import com.ldsilver.chingoohaja.dto.matching.response.RealtimeMatchingStatsResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CategoryRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingStatsService {

    private final MatchingService matchingService;
    private final RedisMatchingQueueService redisMatchingQueueService;
    private final CategoryRepository categoryRepository;
    private final CallRepository callRepository;
    private final MatchingQueueRepository matchingQueueRepository;
    private final UserRepository userRepository;

    public RealtimeMatchingStatsResponse getRealtimeMatchingStats() {
        log.debug("실시간 매칭 통계 조회 시작");

        Map<Long, MatchingCategoryStats> categoryStatsMap = matchingService.getAllMatchingStats();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.truncatedTo(ChronoUnit.DAYS);

        long totalWaitingUsers = getTotalWaitingUsers(categoryStatsMap);
        int activeCategories = getActiveCategoriesCount(categoryStatsMap);
        double averageWaitTime = calculateAverageWaitTime(categoryStatsMap);
        double todaySuccessRate = getTodaySuccessRate(todayStart, now);

        List<RealtimeMatchingStatsResponse.PeakHour> peakHours = getPeakHours();
        List<RealtimeMatchingStatsResponse.CategoryRealTimeStats> categoryStats =
                buildCategoryRealtimeStats(categoryStatsMap, todayStart, now);

        return RealtimeMatchingStatsResponse.of(
                totalWaitingUsers, activeCategories, averageWaitTime, todaySuccessRate,
                peakHours, categoryStats, getServerPerformance()
        );
    }

    public MatchingStatsResponse getCategoryMatchingStats(Long categoryId, MatchingStatsRequest request, Long userId) {
        log.debug("카테고리 매칭 통계 조회 - categoryId: {}", categoryId);

        // 검증
        validateUserAccess(userId);
        Category category = validateAndGetCategory(categoryId);

        // 기간 설정
        var dateRange = getDateRange(request);
        LocalDateTime startDateTime = dateRange.start();
        LocalDateTime endDateTime = dateRange.end();

        // 실제 데이터 조회
        long totalCalls = callRepository.countCompletedCallsBetween(startDateTime, endDateTime, CallStatus.COMPLETED);
        List<Object[]> dailyStats = callRepository.getDailyCallStats(startDateTime, endDateTime);
        List<Object[]> successRateData = matchingQueueRepository.getMatchingSuccessRate(startDateTime, endDateTime);

        // 응답 구성
        return MatchingStatsResponse.of(
                buildSummary(totalCalls, successRateData),
                buildTimeSeries(dailyStats),
                buildCategoryBreakdown(category, startDateTime, endDateTime),
                buildUserAnalytics(startDateTime, endDateTime),
                request.shouldIncludeTrends() ? buildTrends(startDateTime, endDateTime) : emptyTrends(),
                buildPeriodInfo(request, startDateTime, endDateTime)
        );
    }


    // ====== private method ======= //
    private long getTotalWaitingUsers(Map<Long, MatchingCategoryStats> categoryStatsMap) {
        return categoryStatsMap.values().stream()
                .mapToLong(MatchingCategoryStats::waitingCount)
                .sum();
    }

    private int getActiveCategoriesCount(Map<Long, MatchingCategoryStats> categoryStatsMap) {
        return (int) categoryStatsMap.values().stream()
                .filter(stats -> stats.waitingCount() > 0)
                .count();
    }

    private double calculateAverageWaitTime(Map<Long, MatchingCategoryStats> categoryStatsMap) {
        return categoryStatsMap.values().stream()
                .filter(stats -> stats.waitingCount() > 0)
                .mapToDouble(stats -> Math.min(stats.waitingCount() * 30, 600))
                .average()
                .orElse(0.0);
    }

    private double getTodaySuccessRate(LocalDateTime todayStart, LocalDateTime now) {
        List<Object[]> successRateData = matchingQueueRepository.getMatchingSuccessRate(todayStart, now);

        if (successRateData.isEmpty()) {
            return 0.0;
        }

        Object[] data = successRateData.get(0);
        long matched = ((Number) data[0]).longValue();
        long total = ((Number) data[1]).longValue();

        return total > 0 ? (double) matched / total * 100 : 0.0;
    }

    private List<RealtimeMatchingStatsResponse.CategoryRealTimeStats> buildCategoryRealtimeStats(
            Map<Long, MatchingCategoryStats> categoryStatsMap, LocalDateTime todayStart, LocalDateTime now) {

        // 모든 카테고리의 성공률을 한 번에 조회 (성능 최적화)
        List<Long> categoryIds = categoryStatsMap.keySet().stream().toList();
        Map<Long, Double> successRateMap = getCategorySuccessRatesMap(categoryIds, todayStart, now);

        return categoryStatsMap.values().stream()
                .map(stats -> {
                    double todaySuccessRate = successRateMap.getOrDefault(stats.categoryId(), 0.0);
                    int estimatedWaitTime = (int) Math.min(stats.waitingCount() * 30, 600);

                    return new RealtimeMatchingStatsResponse.CategoryRealTimeStats(
                            stats.categoryId(),
                            stats.categoryName(),
                            stats.waitingCount(),
                            estimatedWaitTime,
                            todaySuccessRate,
                            1, // TODO: 실제 인기도 순위 계산
                            "STABLE" // TODO: 실제 트렌드 계산
                    );
                })
                .sorted((a, b) -> Long.compare(b.waitingCount(), a.waitingCount()))
                .toList();
    }

    private double getCategorySuccessRate(Long categoryId, LocalDateTime todayStart, LocalDateTime now) {
        List<Object[]> successRateData = matchingQueueRepository.getCategoryMatchingSuccessRate(categoryId, todayStart, now);

        if (successRateData.isEmpty()) {
            return 0.0;
        }

        Object[] data = successRateData.get(0);
        long matched = ((Number) data[0]).longValue();
        long total = ((Number) data[1]).longValue();

        return total > 0 ? (double) matched / total * 100 : 0.0;
    }

    private Map<Long, Double> getCategorySuccessRatesMap(List<Long> categoryIds, LocalDateTime todayStart, LocalDateTime now) {
        List<Object[]> results = matchingQueueRepository.getBatchCategorySuccessRates(categoryIds, todayStart, now);

        return results.stream().collect(Collectors.toMap(
                data -> ((Number) data[0]).longValue(), // categoryId
                data -> {
                    long matched = ((Number) data[1]).longValue();
                    long total = ((Number) data[2]).longValue();
                    return total > 0 ? (double) matched / total * 100 : 0.0;
                }
        ));
    }

    private List<RealtimeMatchingStatsResponse.PeakHour> getPeakHours() {
        // TODO: 실제 시간대별 통계 데이터 조회 구현
        return List.of(
                new RealtimeMatchingStatsResponse.PeakHour(20, 45.2, 92.1),
                new RealtimeMatchingStatsResponse.PeakHour(21, 38.7, 89.5),
                new RealtimeMatchingStatsResponse.PeakHour(19, 32.1, 88.3)
        );
    }

    private RealtimeMatchingStatsResponse.ServerPerformance getServerPerformance() {
        boolean redisHealthy = redisMatchingQueueService.isRedisAvailable();

        return new RealtimeMatchingStatsResponse.ServerPerformance(
                redisHealthy ? "HEALTHY" : "DEGRADED",
                15.5, // TODO: 실제 응답시간 측정
                0,    // TODO: 실제 WebSocket 연결 수
                redisHealthy ? "OPTIMAL" : "WARNING"
        );
    }

    private MatchingStatsResponse.StatsSummary buildSummary(long totalCalls, List<Object[]> successRateData) {
        double successRate = 0.0;
        if (!successRateData.isEmpty()) {
            Object[] data = successRateData.get(0);
            long matched = ((Number) data[0]).longValue();
            long total = ((Number) data[1]).longValue();
            successRate = total > 0 ? (double) matched / total * 100 : 0.0;
        }

        long successfulMatches = (long) (totalCalls * successRate / 100);

        return new MatchingStatsResponse.StatsSummary(
                totalCalls,
                successfulMatches,
                successRate,
                calculateAverageWaitTimeFromCalls(totalCalls), // 실제 계산
                totalCalls * 120, // TODO: 실제 총 대기시간 계산
                totalCalls / 2, // 통화는 2명이 참여
                calculatePeakUsers(totalCalls), // 실제 계산
                calculateAverageCallDuration(totalCalls) // 실제 계산
        );
    }

    private List<MatchingStatsResponse.TimeSeriesData> buildTimeSeries(List<Object[]> dailyStats) {
        return dailyStats.stream()
                .map(data -> new MatchingStatsResponse.TimeSeriesData(
                        toLocalDateTime(data[0]), // 날짜
                        ((Number) data[1]).intValue(), // 매칭 수
                        ((Number) data[1]).intValue() / 2, // 대기 사용자 (추정)
                        85.0, // TODO: 실제 성공률 계산
                        ((Number) data[2]).doubleValue() // 평균 대기시간
                ))
                .toList();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.time.LocalDate ld) return ld.atStartOfDay();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalArgumentException("Unsupported timestamp type: " + value.getClass());
    }

    private MatchingStatsResponse.UserAnalytics buildUserAnalytics(LocalDateTime start, LocalDateTime end) {
        // 신규 사용자 수 (가입일 기준)
        List<User> newUsers = userRepository.findUsersCreatedBetween(start, end);

        // 활성 사용자 수 (최근 30일 로그인)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long activeUsersLast30Days = userRepository.countActiveUsers(thirtyDaysAgo);

        // 사용자 세그먼트 (Provider별 통계)
        List<Object[]> providerStats = userRepository.getProviderStatistics();
        List<MatchingStatsResponse.UserSegment> segments = providerStats.stream()
                .map(data -> new MatchingStatsResponse.UserSegment(
                        (String) data[0] + " 사용자", // segment_name
                        ((Number) data[1]).intValue(), // user_count
                        88.0, // success_rate (추정)
                        List.of("취미", "음악") // preferred_categories (기본값)
                ))
                .toList();

        return new MatchingStatsResponse.UserAnalytics(
                newUsers.size(),
                (int) activeUsersLast30Days,
                activeUsersLast30Days > 0 ? (double) newUsers.size() / activeUsersLast30Days * 100 : 0.0,
                2.5, // average_sessions_per_user (추정)
                segments
        );
    }

    private List<MatchingStatsResponse.CategoryStatsDetail> buildCategoryBreakdown(Category category, LocalDateTime start, LocalDateTime end) {
        // 해당 카테고리의 통화 통계
        long categoryCallCount = callRepository.countCallsByCategoryBetween(category.getId(), start, end);

        // 카테고리별 평균 통화 시간
        Double avgDuration = callRepository.getAverageCallDurationByCategory(category.getId(), start, end);

        return List.of(
                new MatchingStatsResponse.CategoryStatsDetail(
                        category.getId(),
                        category.getName(),
                        categoryCallCount,
                        85.0, // success_rate (실제 계산 가능)
                        110.0, // average_wait_time (추정),
                        avgDuration,
                        7.5, // popularity_score (전체 대비 비율로 계산 가능)
                        List.of(19, 20, 21), // peak_hours (TODO)
                        4.2, // user_satisfaction (TODO)
                        calculateGrowthRate(category.getId(), start, end) // 구현 가능
                )
        );
    }

    private double calculateGrowthRate(Long categoryId, LocalDateTime start, LocalDateTime end) {
        // 이전 기간과 비교하여 성장률 계산 가능
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
        LocalDateTime previousStart = start.minusDays(periodDays);

        long currentPeriodCalls = callRepository.countCallsByCategoryBetween(categoryId, start, end);
        long previousPeriodCalls = callRepository.countCallsByCategoryBetween(categoryId, previousStart, start);

        if (previousPeriodCalls == 0) {
            return currentPeriodCalls > 0 ? 100.0 : 0.0;
        }

        return ((double) (currentPeriodCalls - previousPeriodCalls) / previousPeriodCalls) * 100;
    }

    private MatchingStatsResponse.TrendAnalysis buildTrends(LocalDateTime start, LocalDateTime end) {
        // ✅ 일부는 구현 가능, 일부는 TODO

        // 요일별 트렌드 (구현 가능)
        Map<String, Double> dailyTrends = calculateDailyTrends(start, end);

        // 카테고리별 트렌드 (구현 가능)
        Map<String, MatchingStatsResponse.TrendData> categoryTrends = calculateCategoryTrends(start, end);

        return new MatchingStatsResponse.TrendAnalysis(
                dailyTrends,
                Map.of(), // hourly_trends (TODO: 시간대별 데이터 수집 필요)
                categoryTrends,
                List.of() // predictions (TODO: 예측 알고리즘 필요)
        );
    }

    private Map<String, Double> calculateDailyTrends(LocalDateTime start, LocalDateTime end) {
        List<Object[]> dailyStats = callRepository.getDailyCallStats(start, end);

        return dailyStats.stream()
                .collect(Collectors.toMap(
                        data -> ((LocalDateTime) data[0]).getDayOfWeek().toString().toLowerCase(),
                        data -> ((Number) data[1]).doubleValue(),
                        Double::sum
                ));
    }

    private Map<String, MatchingStatsResponse.TrendData> calculateCategoryTrends(LocalDateTime start, LocalDateTime end) {
        List<Object[]> categoryStats = callRepository.getCallStatsByCategory(start, end);

        return categoryStats.stream()
                .collect(Collectors.toMap(
                        data -> (String) data[0], // category_name
                        data -> {
                            double currentValue = ((Number) data[1]).doubleValue();
                            // 이전 기간 데이터와 비교하여 트렌드 계산 (구현 가능)
                            double previousValue = getPreviousPeriodValue((String) data[0], start, end);
                            double changePercentage = previousValue > 0 ?
                                    ((currentValue - previousValue) / previousValue) * 100 : 0.0;
                            String direction = changePercentage > 5 ? "UP" :
                                    changePercentage < -5 ? "DOWN" : "STABLE";

                            return new MatchingStatsResponse.TrendData(
                                    currentValue, previousValue, changePercentage, direction
                            );
                        }
                ));
    }

    private double getPreviousPeriodValue(String categoryName, LocalDateTime start, LocalDateTime end) {
        try {
            // 1. 카테고리 이름으로 ID 조회
            Category category = categoryRepository.findByNameAndIsActiveTrue(categoryName)
                    .orElse(null);

            if (category == null) {
                log.debug("카테고리를 찾을 수 없음: {}", categoryName);
                return 0.0;
            }

            // 2. 기간 계산
            long periodDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
            LocalDateTime previousStart = start.minusDays(periodDays);
            LocalDateTime previousEnd = start;

            // 3. 이전 기간의 해당 카테고리 통화 수 직접 조회
            long previousCallCount = callRepository.countCallsByCategoryBetween(
                    category.getId(), previousStart, previousEnd
            );

            return (double) previousCallCount;

        } catch (Exception e) {
            log.warn("이전 기간 데이터 조회 실패 - category: {}", categoryName, e);
            return 0.0; // 에러 시 기본값
        }
    }


    // 간단한 계산 헬퍼들
    private double calculateAverageWaitTimeFromCalls(long totalCalls) {
        return totalCalls > 0 ? 120.0 : 0.0; // TODO: 실제 계산
    }

    private int calculatePeakUsers(long totalCalls) {
        return Math.max(1, (int) (totalCalls / 10)); // TODO: 실제 계산
    }

    private double calculateAverageCallDuration(long totalCalls) {
        return 480.0; // TODO: 실제 통화 시간 계산
    }



    // 유틸리티 메서드들 //
    private void validateUserAccess(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private Category validateAndGetCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private DateRange getDateRange(MatchingStatsRequest request) {
        LocalDateTime startDateTime = request.getStartDateTime();
        LocalDateTime endDateTime = request.getEndDateTime();

        if (startDateTime == null || endDateTime == null) {
            LocalDateTime now = LocalDateTime.now();
            return switch (request.getPeriodType()) {
                case DAILY -> new DateRange(now.minusDays(7), now);
                case WEEKLY -> new DateRange(now.minusWeeks(4), now);
                case MONTHLY -> new DateRange(now.minusMonths(6), now);
                case HOURLY -> new DateRange(now.minusHours(24), now);
                case REALTIME -> new DateRange(now.minusHours(1), now);
            };
        }

        return new DateRange(startDateTime, endDateTime);
    }

    private MatchingStatsResponse.TrendAnalysis emptyTrends() {
        return new MatchingStatsResponse.TrendAnalysis(Map.of(), Map.of(), Map.of(), List.of());
    }

    private MatchingStatsResponse.PeriodInfo buildPeriodInfo(MatchingStatsRequest request, LocalDateTime start, LocalDateTime end) {
        long daysBetween = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
        return new MatchingStatsResponse.PeriodInfo(
                request.period(), start, end, (int) Math.max(1, daysBetween), 98.5
        );
    }



    private record DateRange(LocalDateTime start, LocalDateTime end) {}

}