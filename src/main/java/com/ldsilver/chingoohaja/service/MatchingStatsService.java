package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.dto.matching.MatchingCategoryStats;
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

        return categoryStatsMap.values().stream()
                .map(stats -> {
                    double todaySuccessRate = getCategorySuccessRate(stats.categoryId(), todayStart, now);
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

    private double getCategorySuccessRate(Long categoryId, LocalDateTime start, LocalDateTime end) {
        // TODO: 카테고리별 성공률 계산 로직 구현
        // 임시로 전체 성공률 사용
        return getTodaySuccessRate(start, end);
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


    // 유틸리티 메서드들 //
    private void validateUserAccess(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateCategoryAccess(Long categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

}