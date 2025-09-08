package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.dto.matching.MatchingCategoryStats;
import com.ldsilver.chingoohaja.dto.matching.request.MatchingStatsRequest;
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

        try {
            Map<Long, MatchingCategoryStats> categoryStatsMap = matchingService.getAllMatchingStats();

            long totalWaitingUsers = categoryStatsMap.values().stream()
                    .mapToLong(MatchingCategoryStats::waitingCount)
                    .sum();

            int activeCategories = (int) categoryStatsMap.values().stream()
                    .filter(stats -> stats.waitingCount() > 0)
                    .count();

            double averageWaitTime = calculateAverageWaitTime(categoryStatsMap);
            double todaySuccessRate = calculateTodaySuccessRate();

            List<RealtimeMatchingStatsResponse.PeakHour> peakHours = generatePeakHours();

            List<RealtimeMatchingStatsResponse.CategoryRealTimeStats> categoryStats =
                    categoryStatsMap.values().stream()
                            .map(RealtimeMatchingStatsResponse.CategoryRealTimeStats::from)
                            .sorted((a,b) -> Long.compare(b.waitingCount(), a.waitingCount()))
                            .toList();

            RealtimeMatchingStatsResponse.ServerPerformance serverPerformance = buildServerPerformance();

            log.debug("실시간 매칭 통계 조회 완료 - 총 대기자: {}, 활성 카테고리: {}",
                    totalWaitingUsers, activeCategories);

            return RealtimeMatchingStatsResponse.of(
                    totalWaitingUsers,
                    activeCategories,
                    averageWaitTime,
                    todaySuccessRate,
                    peakHours,
                    categoryStats,
                    serverPerformance
            );
        } catch (Exception e) {
            log.error("실시간 매칭 통계 조회 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "통계 조회 중 오류가 발생했습니다.");
        }
    }

    public MatchingStatsResponse getCategoryMatchingStats(Long categoryId, MatchingStatsRequest request, Long userId) {

    }


    private double calculateAverageWaitTime(Map<Long, MatchingCategoryStats> categoryStatsMap) {
        return categoryStatsMap.values().stream()
                .filter(stats -> stats.waitingCount() > 0)
                .mapToDouble(stats -> Math.min(stats.waitingCount() * 30, 600))
                .average()
                .orElse(0.0);
    }

    private double calculateTodaySuccessRate() {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Object[]> successRateData = matchingQueueRepository.getMatchingSuccessRate(startOfDay, endOfDay);

        if (successRateData.isEmpty()) {
            return 85.0; // 기본값
        }

        Object[] data = successRateData.get(0);
        long matched = ((Number) data[0]).longValue();
        long total = ((Number) data[1]).longValue();

        return total > 0 ? (double) matched / total * 100 : 85.0;
    }

    private List<RealtimeMatchingStatsResponse.PeakHour> generatePeakHours() {
        // 실제 구현에서는 시간대별 통계 데이터를 조회
        return List.of(
                new RealtimeMatchingStatsResponse.PeakHour(20, 45.2, 92.1),
                new RealtimeMatchingStatsResponse.PeakHour(21, 38.7, 89.5),
                new RealtimeMatchingStatsResponse.PeakHour(19, 32.1, 88.3)
        );
    }

    private RealtimeMatchingStatsResponse.ServerPerformance buildServerPerformance() {
        boolean redisHealthy = redisMatchingQueueService.isRedisAvailable();

        return new RealtimeMatchingStatsResponse.ServerPerformance(
                redisHealthy ? "HEALTHY" : "DEGRADED",
                15.5, // 평균 응답시간 (ms)
                0,    // 현재 활성 연결 수 (WebSocket)
                redisHealthy ? "OPTIMAL" : "WARNING"
        );
    }
    
}