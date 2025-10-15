package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.user.response.UserActivityStatsResponse;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallStatisticsRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserActivityStatsService {

    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final CallStatisticsRepository callStatisticsRepository;

    public UserActivityStatsResponse getUserActivityStats(Long userId, String period) {
        log.debug("사용자 활동 통계 조회 - userId: {}, period: {}", userId, period);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 주간 통계
        UserActivityStatsResponse.WeeklyStats weeklyStats = getWeeklyStats(user);

        // 분기 통계 (period가 "quarter"인 경우에만 계산, 아니면 기본값)
        UserActivityStatsResponse.QuarterlyStats quarterlyStats = getQuarterlyStats(user);

        // 추가 통계
        UserActivityStatsResponse.AdditionalStats additionalStats = getAdditionalStats(user);

        log.info("사용자 활동 통계 조회 완료 - userId: {}, weeklyCallCount: {}, quarterlyCallCount: {}",
                userId, weeklyStats.callCount(), quarterlyStats.callCount());

        return UserActivityStatsResponse.of(weeklyStats, quarterlyStats, additionalStats);
    }

    private UserActivityStatsResponse.WeeklyStats getWeeklyStats(User user) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6); // 오늘 포함 7일

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        // ✅ 주간 기간 내 완료된 통화 횟수
        long callCount = callRepository.countByUserAndStatusAndDateBetween(
                user, CallStatus.COMPLETED, startDateTime, endDateTime);

        // ✅ 주간 기간 내 총 통화 시간 (초)
        long totalDurationSeconds = callRepository.sumDurationByUserAndDateBetween(
                user, startDateTime, endDateTime);
        int totalDurationMinutes = (int) (totalDurationSeconds / 60);

        return new UserActivityStatsResponse.WeeklyStats(
                (int) callCount,
                totalDurationMinutes,
                startDate,
                endDate
        );
    }

    private UserActivityStatsResponse.QuarterlyStats getQuarterlyStats(User user) {
        LocalDate today = LocalDate.now();
        int quarter = getQuarter(today);
        LocalDate startDate = getQuarterStartDate(today);
        LocalDate endDate = getQuarterEndDate(today);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        // ✅ 분기 기간 내 완료된 통화 횟수
        long callCount = callRepository.countByUserAndStatusAndDateBetween(
                user, CallStatus.COMPLETED, startDateTime, endDateTime);

        // ✅ 분기 기간 내 총 통화 시간 (초)
        long totalDurationSeconds = callRepository.sumDurationByUserAndDateBetween(
                user, startDateTime, endDateTime);
        int totalDurationMinutes = (int) (totalDurationSeconds / 60);

        return new UserActivityStatsResponse.QuarterlyStats(
                (int) callCount,
                totalDurationMinutes,
                startDate,
                endDate,
                quarter
        );
    }

    private UserActivityStatsResponse.QuarterlyStats getEmptyQuarterlyStats() {
        LocalDate today = LocalDate.now();
        int quarter = getQuarter(today);
        LocalDate startDate = getQuarterStartDate(today);
        LocalDate endDate = getQuarterEndDate(today);

        return new UserActivityStatsResponse.QuarterlyStats(
                0, 0, startDate, endDate, quarter
        );
    }

    private UserActivityStatsResponse.AdditionalStats getAdditionalStats(User user) {
        // 평균 통화 시간 (전체 기간)
        Double averageDuration = callStatisticsRepository.getAverageDurationByUserId(user.getId());
        int averageCallDurationMinutes = averageDuration != null ?
                (int) (averageDuration / 60) : 0;

        // 가장 많이 사용한 카테고리 (전체 기간)
        UserActivityStatsResponse.MostUsedCategory mostUsedCategory = getMostUsedCategory(user);

        // 총 데이터 사용량 (MB) (전체 기간)
        Long totalDataUsageBytes = callStatisticsRepository.getTotalDataUsageByUserId(user.getId());
        double totalDataUsageMB = totalDataUsageBytes != null ?
                totalDataUsageBytes / (1024.0 * 1024.0) : 0.0;

        // 평균 네트워크 품질 (전체 기간)
        Double averageNetworkQuality = callStatisticsRepository.getAverageNetworkQualityByUserId(user.getId());
        double networkQuality = averageNetworkQuality != null ? averageNetworkQuality : 0.0;

        return new UserActivityStatsResponse.AdditionalStats(
                averageCallDurationMinutes,
                mostUsedCategory,
                Math.round(totalDataUsageMB * 100.0) / 100.0, // 소수점 2자리
                Math.round(networkQuality * 10.0) / 10.0 // 소수점 1자리
        );
    }

    private UserActivityStatsResponse.MostUsedCategory getMostUsedCategory(User user) {
        List<Object[]> categoryStats = callRepository.getUserCallStatsByCategory(user.getId());

        if (categoryStats.isEmpty()) {
            return UserActivityStatsResponse.MostUsedCategory.empty();
        }

        // 가장 많이 사용한 카테고리 (첫 번째 항목)
        Object[] mostUsed = categoryStats.get(0);
        Long categoryId = ((Number) mostUsed[0]).longValue();
        String categoryName = (String) mostUsed[1];

        return new UserActivityStatsResponse.MostUsedCategory(categoryId, categoryName);
    }

    private int getQuarter(LocalDate date) {
        int month = date.getMonthValue();
        return (month - 1) / 3 + 1; // 1분기: 1-3월, 2분기: 4-6월, ...
    }

    private LocalDate getQuarterStartDate(LocalDate date) {
        int quarter = getQuarter(date);
        int startMonth = (quarter - 1) * 3 + 1;
        return LocalDate.of(date.getYear(), startMonth, 1);
    }

    private LocalDate getQuarterEndDate(LocalDate date) {
        int quarter = getQuarter(date);
        int endMonth = quarter * 3;
        return LocalDate.of(date.getYear(), endMonth, 1).withDayOfMonth(
                LocalDate.of(date.getYear(), endMonth, 1).lengthOfMonth()
        );
    }
}