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
import com.ldsilver.chingoohaja.repository.EvaluationRepository;
import com.ldsilver.chingoohaja.repository.MatchingQueueRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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
    private final EvaluationRepository evaluationRepository;

    public RealtimeMatchingStatsResponse getRealtimeMatchingStats() {
        log.debug("실시간 매칭 통계 조회 시작");

        Map<Long, MatchingCategoryStats> categoryStatsMap = matchingService.getAllMatchingStats();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.truncatedTo(ChronoUnit.DAYS);

        long totalWaitingUsers = getTotalWaitingUsers(categoryStatsMap);
        int activeCategories = getActiveCategoriesCount(categoryStatsMap);
        double averageWaitTime = calculateAverageWaitTime(categoryStatsMap);
        double todaySuccessRate = getTodaySuccessRate(todayStart, now);

        List<RealtimeMatchingStatsResponse.PeakHour> peakHours = getPeakHours(todayStart, now);
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
                buildSummary(totalCalls, successRateData, startDateTime, endDateTime),
                buildTimeSeries(dailyStats, startDateTime, endDateTime),
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

        // 카테고리별 트렌드를 한 번에 계산 (N+1 쿼리 방지)
        Map<Long, String> trendMap = getCategoryTrendMap(categoryIds, todayStart, now);

        // 인기도 순위 계산 (대기 인원 기준)
        List<Long> sortedCategoryIds = categoryStatsMap.values().stream()
                .sorted((a, b) -> Long.compare(b.waitingCount(), a.waitingCount()))
                .map(MatchingCategoryStats::categoryId)
                .toList();

        return categoryStatsMap.values().stream()
                .map(stats -> {
                    double todaySuccessRate = successRateMap.getOrDefault(stats.categoryId(), 0.0);
                    int estimatedWaitTime = (int) Math.min(stats.waitingCount() * 30, 600);
                    int popularityRank = sortedCategoryIds.indexOf(stats.categoryId()) + 1;

                    // Map에서 트렌드 조회 (O(1))
                    String trend = trendMap.getOrDefault(stats.categoryId(), "STABLE");

                    return new RealtimeMatchingStatsResponse.CategoryRealTimeStats(
                            stats.categoryId(),
                            stats.categoryName(),
                            stats.waitingCount(),
                            estimatedWaitTime,
                            todaySuccessRate,
                            popularityRank,
                            trend
                    );
                })
                .sorted((a, b) -> Long.compare(b.waitingCount(), a.waitingCount()))
                .toList();
    }

    /**
     * 카테고리별 트렌드를 한 번에 계산하여 Map으로 반환 (N+1 쿼리 방지)
     */
    private Map<Long, String> getCategoryTrendMap(List<Long> categoryIds, LocalDateTime todayStart, LocalDateTime now) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            LocalDateTime yesterdayStart = todayStart.minusDays(1);

            // 오늘과 어제의 통화 수를 각각 한 번에 조회 (2번 쿼리로 최적화)
            Map<Long, Long> todayCallsMap = getCallCountMapByCategories(categoryIds, todayStart, now);
            Map<Long, Long> yesterdayCallsMap = getCallCountMapByCategories(categoryIds, yesterdayStart, todayStart);

            // 트렌드 계산
            return categoryIds.stream()
                    .collect(Collectors.toMap(
                            categoryId -> categoryId,
                            categoryId -> {
                                long todayCalls = todayCallsMap.getOrDefault(categoryId, 0L);
                                long yesterdayCalls = yesterdayCallsMap.getOrDefault(categoryId, 0L);
                                return calculateTrendFromCounts(todayCalls, yesterdayCalls);
                            }
                    ));
        } catch (Exception e) {
            log.warn("카테고리 트렌드 배치 계산 실패", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 여러 카테고리의 통화 수를 한 번에 조회하여 Map으로 반환
     */
    private Map<Long, Long> getCallCountMapByCategories(List<Long> categoryIds, LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = callRepository.countCallsByCategoryIdsBetween(categoryIds, start, end);

        return results.stream()
                .collect(Collectors.toMap(
                        data -> ((Number) data[0]).longValue(),
                        data -> ((Number) data[1]).longValue()
                ));
    }

    /**
     * 통화 수 기반 트렌드 계산
     */
    private String calculateTrendFromCounts(long todayCalls, long yesterdayCalls) {
        if (yesterdayCalls == 0) {
            return todayCalls > 0 ? "UP" : "STABLE";
        }

        double changeRate = ((double) (todayCalls - yesterdayCalls) / yesterdayCalls) * 100;

        if (changeRate > 20) return "UP";
        if (changeRate < -20) return "DOWN";
        return "STABLE";
    }

    private Map<Long, Double> getCategorySuccessRatesMap(List<Long> categoryIds, LocalDateTime todayStart, LocalDateTime now) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

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

    /**
     * 피크 시간대 조회 (실제 DB 기반)
     */
    private List<RealtimeMatchingStatsResponse.PeakHour> getPeakHours(LocalDateTime todayStart, LocalDateTime now) {
        try {
            List<Object[]> hourlyData = callRepository.getCallCountByHour(todayStart, now);

            // 전체 통화 수를 한 번만 조회 (성능 최적화)
            long totalCalls = callRepository.countCompletedCallsBetween(
                    todayStart, now, CallStatus.COMPLETED
            );

            // 시간대별 성공률을 한 번에 조회 (N+1 쿼리 방지)
            Map<Integer, Double> hourlySuccessRateMap = getHourlySuccessRateMap(todayStart, now);

            // 시간대별 통화 수를 기반으로 상위 3개 추출
            return hourlyData.stream()
                    .map(data -> {
                        int hour = ((Number) data[0]).intValue();
                        long count = ((Number) data[1]).longValue();

                        // 전체 대비 비율 계산
                        double percentage = totalCalls > 0 ? (count * 100.0 / totalCalls) : 0.0;

                        // 성공률 (실제 DB 데이터 기반)
                        double successRate = hourlySuccessRateMap.getOrDefault(hour, 0.0);

                        return new RealtimeMatchingStatsResponse.PeakHour(hour, percentage, successRate);
                    })
                    .sorted((a, b) -> Double.compare(b.usagePercentage(), a.usagePercentage()))
                    .limit(3)
                    .toList();

        } catch (Exception e) {
            log.warn("피크 시간대 조회 실패", e);
            // 기본값 반환
            return List.of(
                    new RealtimeMatchingStatsResponse.PeakHour(20, 30.0, 0.0),
                    new RealtimeMatchingStatsResponse.PeakHour(21, 25.0, 0.0),
                    new RealtimeMatchingStatsResponse.PeakHour(19, 20.0, 0.0)
            );
        }
    }

    /**
     * 시간대별 매칭 성공률을 한 번에 조회하여 Map으로 반환 (N+1 쿼리 방지)
     */
    private Map<Integer, Double> getHourlySuccessRateMap(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> hourlySuccessRates = matchingQueueRepository.getHourlyMatchingSuccessRates(start, end);

            return hourlySuccessRates.stream()
                    .collect(Collectors.toMap(
                            data -> ((Number) data[0]).intValue(),
                            data -> {
                                long matched = ((Number) data[1]).longValue();
                                long total = ((Number) data[2]).longValue();
                                return total > 0 ? (double) matched / total * 100 : 0.0;
                            },
                            (existing, replacement) -> existing // 중복 키 처리
                    ));
        } catch (Exception e) {
            log.warn("시간대별 성공률 배치 조회 실패", e);
            return Collections.emptyMap();
        }
    }

    private RealtimeMatchingStatsResponse.ServerPerformance getServerPerformance() {
        boolean redisHealthy = redisMatchingQueueService.isRedisAvailable();

        return new RealtimeMatchingStatsResponse.ServerPerformance(
                redisHealthy ? "HEALTHY" : "DEGRADED",
                15.5, // TODO: 실제 응답시간 측정 (선택적)
                0,    // TODO: 실제 WebSocket 연결 수 (선택적)
                redisHealthy ? "OPTIMAL" : "WARNING"
        );
    }

    /**
     * 요약 통계 생성 (실제 계산)
     */
    private MatchingStatsResponse.StatsSummary buildSummary(
            long totalCalls,
            List<Object[]> successRateData,
            LocalDateTime start,
            LocalDateTime end
    ) {
        long successfulMatches = 0;
        double successRate = 0.0;
        if (!successRateData.isEmpty()) {
            Object[] data = successRateData.get(0);
            long matched = ((Number) data[0]).longValue();
            long total = ((Number) data[1]).longValue();
            successRate = total > 0 ? (double) matched / total * 100 : 0.0;
            successfulMatches = matched;
        }

        // 실제 평균 대기시간 계산
        double averageWaitTime = calculateActualAverageWaitTime(start, end);

        // 총 대기시간 계산
        long totalWaitTime = (long) (averageWaitTime * totalCalls * 2); // 2명이 대기

        // 피크 사용자 수 (해당 기간 최대 동시 대기)
        int peakUsers = calculatePeakUsers(start, end);

        // 평균 통화 시간 계산
        double averageCallDuration = calculateAverageCallDuration(start, end);

        return new MatchingStatsResponse.StatsSummary(
                totalCalls,
                successfulMatches,
                successRate,
                averageWaitTime,
                totalWaitTime,
                totalCalls * 2, // 통화는 2명이 참여
                peakUsers,
                averageCallDuration
        );
    }

    /**
     * 실제 평균 대기시간 계산
     */
    private double calculateActualAverageWaitTime(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> queueData = matchingQueueRepository.getMatchingSuccessRate(start, end);

            if (queueData.isEmpty()) {
                return 0.0;
            }

            // 간단한 추정: 매칭 수에 기반한 평균 대기시간
            Object[] data = queueData.get(0);
            long total = ((Number) data[1]).longValue();

            // 대기열이 길수록 대기시간 증가 (최대 10분)
            return Math.min(total * 5.0, 600.0); // 5초 * 대기자 수

        } catch (Exception e) {
            log.warn("평균 대기시간 계산 실패", e);
            return 120.0; // 기본값 2분
        }
    }

    /**
     * 피크 사용자 수 계산 (해당 기간 최대 동시 대기)
     */
    private int calculatePeakUsers(LocalDateTime start, LocalDateTime end) {
        try {
            // 시간대별 통화 수를 기반으로 추정
            List<Object[]> hourlyData = callRepository.getCallCountByHour(start, end);

            if (hourlyData.isEmpty()) {
                return 0;
            }

            // 최대 시간당 통화 수
            long maxHourlyCallsValue = hourlyData.stream()
                    .mapToLong(data -> ((Number) data[1]).longValue())
                    .max()
                    .orElse(0L);

            int maxHourlyCalls = (int) maxHourlyCallsValue;

            // 통화 1건당 평균 2명이 대기했다고 가정
            return maxHourlyCalls * 2;

        } catch (Exception e) {
            log.warn("피크 사용자 수 계산 실패", e);
            return 10; // 기본값
        }
    }

    /**
     * 실제 평균 통화 시간 계산
     */
    private double calculateAverageCallDuration(LocalDateTime start, LocalDateTime end) {
        try {
            Double avgDuration = callRepository.getAverageDurationMinutesBetween(start, end);
            return avgDuration != null ? avgDuration * 60 : 0.0; // 분을 초로 변환
        } catch (Exception e) {
            log.warn("평균 통화 시간 계산 실패", e);
            return 480.0; // 기본값 8분
        }
    }

    private List<MatchingStatsResponse.TimeSeriesData> buildTimeSeries(
            List<Object[]> dailyStats,
            LocalDateTime start,
            LocalDateTime end
    ) {
        // 일별 성공률을 한 번에 조회 (N+1 쿼리 방지)
        Map<java.time.LocalDate, Double> dailySuccessRateMap = getDailySuccessRateMap(start, end);

        return dailyStats.stream()
                .map(data -> {
                    LocalDateTime date = toLocalDateTime(data[0]);
                    int matchCount = ((Number) data[1]).intValue();
                    double avgWaitTime = ((Number) data[2]).doubleValue();

                    // Map에서 성공률 조회 (O(1))
                    java.time.LocalDate localDate = date.toLocalDate();
                    double successRate = dailySuccessRateMap.getOrDefault(localDate, 0.0);

                    // 대기 사용자 수 (매칭 수 기반 추정)
                    int waitingUsers = matchCount * 2; // 매칭 1건당 2명

                    return new MatchingStatsResponse.TimeSeriesData(
                            date,
                            matchCount,
                            waitingUsers,
                            successRate,
                            avgWaitTime
                    );
                })
                .toList();
    }

    /**
     * 일별 매칭 성공률을 한 번에 조회하여 Map으로 반환 (N+1 쿼리 방지)
     */
    private Map<java.time.LocalDate, Double> getDailySuccessRateMap(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> dailySuccessRates = matchingQueueRepository.getDailyMatchingSuccessRates(start, end);

            return dailySuccessRates.stream()
                    .collect(Collectors.toMap(
                            data -> toLocalDate(data[0]),
                            data -> {
                                long matched = ((Number) data[1]).longValue();
                                long total = ((Number) data[2]).longValue();
                                return total > 0 ? (double) matched / total * 100 : 0.0;
                            },
                            (existing, replacement) -> existing // 중복 키 처리
                    ));
        } catch (Exception e) {
            log.warn("일별 성공률 배치 조회 실패", e);
            return Collections.emptyMap();
        }
    }

    private static java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.time.LocalDate ld) return ld;
        if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (value instanceof java.sql.Date sd) return sd.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        throw new CustomException(ErrorCode.UNSUPPORTED_DATE_TYPE, value.getClass().getName());
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.time.LocalDate ld) return ld.atStartOfDay();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof java.sql.Date sd) return sd.toLocalDate().atStartOfDay();
        throw new CustomException(ErrorCode.UNSUPPORTED_TIMESTAMP_TYPE, value.getClass().getName());
    }

    private MatchingStatsResponse.UserAnalytics buildUserAnalytics(LocalDateTime start, LocalDateTime end) {
        // 신규 사용자 수 (가입일 기준)
        List<User> newUsers = userRepository.findUsersCreatedBetween(start, end);

        // 활성 사용자 수 (최근 30일 로그인)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long activeUsersLast30Days = userRepository.countActiveUsers(thirtyDaysAgo);

        // Provider별 성공률 조회
        Map<String, Double> providerSuccessRates = getProviderSuccessRates(start, end);

        // Provider별 선호 카테고리 조회
        Map<String, List<String>> providerPreferredCategories = getProviderPreferredCategories(start, end);

        // 사용자 세그먼트 (Provider별 통계)
        List<Object[]> providerStats = userRepository.getProviderStatistics();
        List<MatchingStatsResponse.UserSegment> segments = providerStats.stream()
                .map(data -> {
                    String provider = (String) data[0];
                    int userCount = ((Number) data[1]).intValue();
                    double successRate = providerSuccessRates.getOrDefault(provider, 0.0);
                    List<String> preferredCategories = providerPreferredCategories.getOrDefault(provider, List.of());

                    return new MatchingStatsResponse.UserSegment(
                            provider + " 사용자",
                            userCount,
                            successRate,
                            preferredCategories
                    );
                })
                .toList();

        // 사용자당 평균 세션(통화) 수 조회
        Double averageSessionsPerUser = callRepository.getAverageCallsPerUser(start, end);
        double avgSessions = averageSessionsPerUser != null ? averageSessionsPerUser : 0.0;

        return new MatchingStatsResponse.UserAnalytics(
                newUsers.size(),
                (int) activeUsersLast30Days,
                activeUsersLast30Days > 0 ? (double) newUsers.size() / activeUsersLast30Days * 100 : 0.0,
                avgSessions,
                segments
        );
    }

    /**
     * Provider별 통화 성공률 계산
     */
    private Map<String, Double> getProviderSuccessRates(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = callRepository.getSuccessRateByProvider(start, end);
            return results.stream().collect(Collectors.toMap(
                    data -> (String) data[0], // provider
                    data -> {
                        long completed = ((Number) data[1]).longValue();
                        long total = ((Number) data[2]).longValue();
                        return total > 0 ? (completed * 100.0 / total) : 0.0;
                    },
                    (existing, replacement) -> existing // 중복 키 처리
            ));
        } catch (Exception e) {
            log.warn("Provider별 성공률 계산 실패", e);
            return Map.of();
        }
    }

    /**
     * Provider별 선호 카테고리 조회 (상위 3개)
     */
    private Map<String, List<String>> getProviderPreferredCategories(LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> results = callRepository.getPreferredCategoriesByProvider(start, end);

            // Provider별로 그룹화하고 상위 3개 카테고리만 추출
            return results.stream()
                    .collect(Collectors.groupingBy(
                            data -> (String) data[0], // provider
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> list.stream()
                                            .limit(3) // 상위 3개만
                                            .map(data -> (String) data[1]) // category name
                                            .toList()
                            )
                    ));
        } catch (Exception e) {
            log.warn("Provider별 선호 카테고리 조회 실패", e);
            return Map.of();
        }
    }

    private List<MatchingStatsResponse.CategoryStatsDetail> buildCategoryBreakdown(
            Category category,
            LocalDateTime start,
            LocalDateTime end
    ) {
        // 해당 카테고리의 통화 통계
        long categoryCallCount = callRepository.countCallsByCategoryBetween(category.getId(), start, end);

        // 카테고리별 평균 통화 시간
        Double avgDuration = callRepository.getAverageCallDurationByCategory(category.getId(), start, end);

        double safeAvgDuration = (avgDuration != null) ? avgDuration : 0.0;

        // 실제 성공률 계산
        double successRate = getCategorySuccessRate(category.getId(), start, end);

        // 평균 대기시간 (실제 DB 데이터 기반)
        double avgWaitTime = getCategoryAverageWaitTime(category.getId(), start, end);

        // 인기도 점수 (전체 대비 비율)
        long totalCalls = callRepository.countCompletedCallsBetween(start, end, CallStatus.COMPLETED);
        double popularityScore = totalCalls > 0 ? (categoryCallCount * 100.0 / totalCalls) : 0.0;

        // 피크 시간대 계산
        List<Integer> peakHours = calculateCategoryPeakHours(category.getId(), start, end);

        // 사용자 만족도 (실제 DB 데이터 기반)
        double userSatisfaction = getCategoryUserSatisfaction(category.getId(), start, end);

        return List.of(
                new MatchingStatsResponse.CategoryStatsDetail(
                        category.getId(),
                        category.getName(),
                        categoryCallCount,
                        successRate,
                        avgWaitTime,
                        safeAvgDuration,
                        popularityScore,
                        peakHours,
                        userSatisfaction,
                        calculateGrowthRate(category.getId(), start, end)
                )
        );
    }

    /**
     * 카테고리별 평균 대기시간 조회 (초 단위)
     */
    private double getCategoryAverageWaitTime(Long categoryId, LocalDateTime start, LocalDateTime end) {
        try {
            Double avgWaitTime = callRepository.getAverageWaitTimeByCategory(categoryId, start, end);
            return avgWaitTime != null ? avgWaitTime : 0.0;
        } catch (Exception e) {
            log.warn("카테고리 평균 대기시간 조회 실패 - categoryId: {}", categoryId, e);
            return 0.0;
        }
    }

    /**
     * 카테고리별 사용자 만족도 조회 (0~5 스케일)
     */
    private double getCategoryUserSatisfaction(Long categoryId, LocalDateTime start, LocalDateTime end) {
        try {
            Double satisfaction = evaluationRepository.getAverageSatisfactionByCategory(categoryId, start, end);
            return satisfaction != null ? satisfaction : 0.0;
        } catch (Exception e) {
            log.warn("카테고리 사용자 만족도 조회 실패 - categoryId: {}", categoryId, e);
            return 0.0;
        }
    }

    /**
     * 카테고리 성공률 계산
     */
    private double getCategorySuccessRate(Long categoryId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> successRateData = matchingQueueRepository.getCategoryMatchingSuccessRate(
                categoryId, start, end
        );

        if (successRateData.isEmpty()) {
            return 0.0;
        }

        Object[] data = successRateData.get(0);
        long matched = ((Number) data[0]).longValue();
        long total = ((Number) data[1]).longValue();

        return total > 0 ? (double) matched / total * 100 : 0.0;
    }

    /**
     * 카테고리별 피크 시간대 계산
     */
    private List<Integer> calculateCategoryPeakHours(Long categoryId, LocalDateTime start, LocalDateTime end) {
        try {
            List<Object[]> hourlyData = callRepository.getCallCountByHourByCategory(categoryId, start, end);

            if (hourlyData.isEmpty()) {
                return List.of(19, 20, 21); // 기본값
            }

            // 상위 3개 시간대 추출 (이미 ORDER BY count DESC로 정렬됨)
            return hourlyData.stream()
                    .limit(3)
                    .map(data -> ((Number) data[0]).intValue())
                    .toList();

        } catch (Exception e) {
            log.warn("카테고리 피크 시간대 계산 실패 - categoryId: {}", categoryId, e);
            return List.of(19, 20, 21); // 기본값
        }
    }

    private double calculateGrowthRate(Long categoryId, LocalDateTime start, LocalDateTime end) {
        // 이전 기간과 비교하여 성장률 계산
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
        // 요일별 트렌드
        Map<String, Double> dailyTrends = calculateDailyTrends(start, end);

        // 카테고리별 트렌드
        Map<String, MatchingStatsResponse.TrendData> categoryTrends = calculateCategoryTrends(start, end);

        return new MatchingStatsResponse.TrendAnalysis(
                dailyTrends,
                Map.of(), // hourly_trends (선택적 - 필요시 구현)
                categoryTrends,
                List.of() // predictions (선택적 - ML 필요)
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
            Category category = categoryRepository.findByNameAndIsActiveTrue(categoryName)
                    .orElse(null);

            if (category == null) {
                log.debug("카테고리를 찾을 수 없음: {}", categoryName);
                return 0.0;
            }

            long periodDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
            LocalDateTime previousStart = start.minusDays(periodDays);
            LocalDateTime previousEnd = start;

            long previousCallCount = callRepository.countCallsByCategoryBetween(
                    category.getId(), previousStart, previousEnd
            );

            return (double) previousCallCount;

        } catch (Exception e) {
            log.warn("이전 기간 데이터 조회 실패 - category: {}", categoryName, e);
            return 0.0;
        }
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