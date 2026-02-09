package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.report.Report;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.admin.response.AdminUserListResponse;
import com.ldsilver.chingoohaja.dto.admin.response.CallMonitoringResponse;
import com.ldsilver.chingoohaja.dto.admin.response.DashboardOverviewResponse;
import com.ldsilver.chingoohaja.dto.admin.response.ReportListResponse;
import com.ldsilver.chingoohaja.dto.call.AgoraHealthStatus;
import com.ldsilver.chingoohaja.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final CallRepository callRepository;
    private final ReportRepository reportRepository;
    private final CallRecordingRepository callRecordingRepository;
    private final MatchingQueueRepository matchingQueueRepository;
    private final EvaluationRepository evaluationRepository;
    private final AgoraService agoraService;
    private final DataSource dataSource;
    private final RedisMatchingQueueService redisMatchingQueueService;

    private volatile DashboardOverviewResponse.SystemHealth cachedHealth;
    private volatile long lastHealthCheckTime = 0;
    private static final long HEALTH_CHECK_CACHE_DURATION = 10_000; // 10초

    public DashboardOverviewResponse getDashboardOverview() {
        log.debug("대시보드 개요 생성 시작");

        // 시스템 헬스 체크
        DashboardOverviewResponse.SystemHealth systemHealth = getCachedSystemHealth();

        // 실시간 통계
        DashboardOverviewResponse.RealTimeStats realTimeStats = getRealTimeStats();

        // 오늘의 요약
        DashboardOverviewResponse.TodaySummary todaySummary = getTodaySummary();

        // 오늘의 평가 비율
        DashboardOverviewResponse.EvaluationStats evaluationStats = getEvaluationStats(); // ✅ 추가

        // 최근 알림 (예: 에러, 경고 등)
        List<DashboardOverviewResponse.Alert> recentAlerts = getRecentAlerts();

        log.info("대시보드 개요 생성 완료");

        return new DashboardOverviewResponse(
                systemHealth,
                realTimeStats,
                todaySummary,
                evaluationStats,
                recentAlerts,
                LocalDateTime.now()
        );
    }

    public AdminUserListResponse getUsers(
            int page, int limit, String search, String userType,
            String sortBy, String sortOrder
    ) {
        log.debug("사용자 목록 조회 - page: {}, search: {}", page, search);

        // 정렬 설정
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, mapSortField(sortBy));

        Pageable pageable = PageRequest.of(page - 1, limit, sort);

        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        UserType userTypeEnum = parseUserType(userType);
        Page<User> userPage = userRepository.findBySearchAndUserType(
                searchTerm,
                userTypeEnum,
                pageable
        );

        List<AdminUserListResponse.UserSummary> userSummaries = userPage.getContent().stream()
                .map(this::toUserSummary)
                .toList();

        AdminUserListResponse.Pagination pagination = new AdminUserListResponse.Pagination(
                page,
                userPage.getTotalPages(),
                userPage.getTotalElements(),
                userPage.hasNext()
        );

        return new AdminUserListResponse(userSummaries, pagination);
    }

    public ReportListResponse getReports(int page, int limit, String status) {
        log.debug("신고 목록 조회 - page: {}, status: {}", page, status);

        Pageable pageable = PageRequest.of(
                page - 1,
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Report> reportPage = reportRepository.findAll(pageable);

        List<ReportListResponse.ReportDetail> reportDetails = reportPage.getContent().stream()
                .map(this::toReportDetail)
                .toList();

        ReportListResponse.Pagination pagination = new ReportListResponse.Pagination(
                page,
                reportPage.getTotalPages(),
                reportPage.getTotalElements()
        );

        return new ReportListResponse(reportDetails, pagination);
    }

    public CallMonitoringResponse monitorCalls() {
        log.debug("통화 모니터링 시작");

        // 진행 중인 통화
        List<Call> activeCalls = callRepository.findByCallStatus(CallStatus.IN_PROGRESS);
        List<CallMonitoringResponse.ActiveCallInfo> activeCallInfos = activeCalls.stream()
                .map(this::toActiveCallInfo)
                .toList();

        // 최근 종료된 통화 (최근 1시간)
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Call> recentEndedCalls = callRepository.findRecentEndedCalls(oneHourAgo);
        List<CallMonitoringResponse.EndedCallInfo> endedCallInfos = recentEndedCalls.stream()
                .map(this::toEndedCallInfo)
                .toList();

        // 통화 통계
        CallMonitoringResponse.CallStatistics statistics = getCallStatistics();

        return new CallMonitoringResponse(activeCallInfos, endedCallInfos, statistics);
    }

    // ===== Private Helper Methods ===== //

    private DashboardOverviewResponse.SystemHealth getCachedSystemHealth() {
        long now = System.currentTimeMillis();

        // 캐시가 유효하면 반환
        if (cachedHealth != null && (now - lastHealthCheckTime) < HEALTH_CHECK_CACHE_DURATION) {
            log.debug("캐시된 헬스체크 사용");
            return cachedHealth;
        }

        // 캐시가 없거나 만료되면 새로 체크
        try {
            DashboardOverviewResponse.SystemHealth newHealth = checkSystemHealth();
            cachedHealth = newHealth;
            lastHealthCheckTime = now;
            log.debug("헬스체크 갱신 완료");
            return newHealth;
        } catch (Exception e) {
            log.error("헬스체크 실패", e);
            // 이전 캐시가 있으면 반환, 없으면 UNKNOWN
            return cachedHealth != null ? cachedHealth :
                    new DashboardOverviewResponse.SystemHealth(
                            "UNKNOWN", "UNKNOWN", "UNKNOWN", "DOWN"
                    );
        }
    }

    private DashboardOverviewResponse.SystemHealth checkSystemHealth() {
        // 데이터베이스 상태
        String dbStatus = checkDatabaseHealth() ? "HEALTHY" : "DOWN";

        // Redis 상태
        String redisStatus = redisMatchingQueueService.isRedisAvailable() ? "HEALTHY" : "DOWN";

        // Agora 상태
        AgoraHealthStatus agoraHealth = agoraService.checkHealth();
        String agoraStatus = agoraHealth.isHealthy() ? "HEALTHY" : "DEGRADED";

        // 전체 상태
        String overallStatus = (dbStatus.equals("HEALTHY") &&
                redisStatus.equals("HEALTHY") &&
                agoraStatus.equals("HEALTHY")) ? "HEALTHY" : "DEGRADED";

        return new DashboardOverviewResponse.SystemHealth(
                dbStatus, redisStatus, agoraStatus, overallStatus
        );
    }

    private boolean checkDatabaseHealth() {
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            log.error("데이터베이스 헬스 체크 실패", e);
            return false;
        }
    }

    private DashboardOverviewResponse.RealTimeStats getRealTimeStats() {
        int activeCalls = callRepository.countByCallStatus(CallStatus.IN_PROGRESS);
        int usersInQueue = (int) redisMatchingQueueService.getAllCategoryStats().values()
                .stream().mapToLong(Long::longValue).sum();

        // 통화 중 + 대기 중 = 현재 활성 사용자
        int activeUsersNow = activeCalls * 2 + usersInQueue;

        int recordingsInProgress = callRecordingRepository.countActiveRecordings();

        return new DashboardOverviewResponse.RealTimeStats(
                activeCalls, usersInQueue, activeUsersNow, recordingsInProgress
        );
    }

    private UserType parseUserType(String userType) {
        if (userType == null || userType.trim().isEmpty()) {
            return null;
        }

        try {
            return UserType.valueOf(userType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 UserType: {}", userType);
            return null;
        }
    }


    private DashboardOverviewResponse.TodaySummary getTodaySummary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long totalCalls = callRepository.countCompletedCallsBetween(
                todayStart, now, CallStatus.COMPLETED
        );

        long newUsers = userRepository.findUsersCreatedBetween(todayStart, now).size();

        long reportsCount = reportRepository.countByCreatedAtBetween(todayStart, now);

        // 매칭 성공률 계산
        List<Object[]> successRateData = matchingQueueRepository
                .getMatchingSuccessRate(todayStart, now);
        double matchingSuccessRate = 0.0;
        if (!successRateData.isEmpty()) {
            Object[] data = successRateData.get(0);
            long matched = ((Number) data[0]).longValue();
            long total = ((Number) data[1]).longValue();
            matchingSuccessRate = total > 0 ? (double) matched / total * 100 : 0.0;
        }

        // 오늘의 평가 참여율 계산
        long todayEvaluations = evaluationRepository.countEvaluationsBetween(todayStart, now);
        double evaluationRate = totalCalls > 0 ?
                (double) todayEvaluations / (totalCalls * 2) * 100 : 0.0; // 통화당 2명이 평가 가능
        evaluationRate = Math.round(evaluationRate * 10.0) / 10.0; // 소수점 1자리

        List<Object[]> feedbackStats = evaluationRepository
                .countByFeedbackTypeBetween(todayStart, now);
        double positiveRate = calculatePositiveRate(feedbackStats);

        return new DashboardOverviewResponse.TodaySummary(
                totalCalls, newUsers, reportsCount,
                matchingSuccessRate, evaluationRate, positiveRate
        );
    }

    private DashboardOverviewResponse.EvaluationStats getEvaluationStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 총 평가 수
        long totalEvaluations = evaluationRepository.countEvaluationsBetween(todayStart, now);

        // 평가 가능한 총 기회 수 (완료된 통화 * 2)
        long completedCalls = callRepository.countCompletedCallsBetween(
                todayStart, now, CallStatus.COMPLETED
        );
        long totalEvaluationOpportunities = completedCalls * 2;

        // 평가 참여율
        double participationRate = totalEvaluationOpportunities > 0 ?
                (double) totalEvaluations / totalEvaluationOpportunities * 100 : 0.0;
        participationRate = Math.round(participationRate * 10.0) / 10.0;

        // 피드백 타입별 개수
        List<Object[]> feedbackStats = evaluationRepository
                .countByFeedbackTypeBetween(todayStart, now);

        long positiveCount = 0;
        long neutralCount = 0;
        long negativeCount = 0;

        for (Object[] stat : feedbackStats) {
            String feedbackType = stat[0] != null ? stat[0].toString() : null;
            long count = ((Number) stat[1]).longValue();

            if (feedbackType == null) {
                continue;
            }

            switch (feedbackType) {
                case "POSITIVE" -> positiveCount = count;
                case "NEUTRAL" -> neutralCount = count;
                case "NEGATIVE" -> negativeCount = count;
            }
        }

        // 긍정/부정 비율
        double positivePercentage = totalEvaluations > 0 ?
                (double) positiveCount / totalEvaluations * 100 : 0.0;
        double negativePercentage = totalEvaluations > 0 ?
                (double) negativeCount / totalEvaluations * 100 : 0.0;

        positivePercentage = Math.round(positivePercentage * 10.0) / 10.0;
        negativePercentage = Math.round(negativePercentage * 10.0) / 10.0;

        log.debug("평가 통계 - 총: {}, 참여율: {}%, 긍정: {}%, 부정: {}%",
                totalEvaluations, participationRate, positivePercentage, negativePercentage);

        return new DashboardOverviewResponse.EvaluationStats(
                totalEvaluations,
                participationRate,
                positiveCount,
                neutralCount,
                negativeCount,
                positivePercentage,
                negativePercentage
        );
    }

    // 긍정 평가 비율 계산
    private double calculatePositiveRate(List<Object[]> feedbackStats) {
        if (feedbackStats.isEmpty()) {
            return 0.0;
        }

        long positiveCount = 0;
        long totalCount = 0;

        for (Object[] stat : feedbackStats) {
            String feedbackType = stat[0] != null ? stat[0].toString() : null;
            long count = ((Number) stat[1]).longValue();
            totalCount += count;

            if (feedbackType == null) {
                continue;
            }

            if ("POSITIVE".equals(feedbackType)) {
                positiveCount = count;
            }
        }

        double rate = totalCount > 0 ? (double) positiveCount / totalCount * 100 : 0.0;
        return Math.round(rate * 10.0) / 10.0; // 소수점 1자리
    }

    private List<DashboardOverviewResponse.Alert> getRecentAlerts() {
        List<DashboardOverviewResponse.Alert> alerts = new ArrayList<>();
        LocalDateTime recentTime = LocalDateTime.now().minusHours(1);

        // 1. 녹음 실패 체크
        int failedRecordings = callRecordingRepository.countFailedRecordingsSince(recentTime);
        if (failedRecordings > 0) {
            alerts.add(new DashboardOverviewResponse.Alert(
                    "WARNING",
                    String.format("최근 1시간 내 %d건의 녹음 실패 발생", failedRecordings),
                    LocalDateTime.now()
            ));
        }

        // 2. 비정상 종료 통화 체크 (5분 이하 통화가 많은 경우)
        long shortCalls = callRepository.countShortCallsSince(recentTime, 300); // 5분 = 300초
        if (shortCalls > 10) {
            alerts.add(new DashboardOverviewResponse.Alert(
                    "WARNING",
                    String.format("최근 1시간 내 %d건의 짧은 통화(5분 이하) 발생", shortCalls),
                    LocalDateTime.now()
            ));
        }

         // 3. 평가 참여율 경고
         DashboardOverviewResponse.EvaluationStats evalStats = getEvaluationStats();
         if (evalStats.evaluationParticipationRate() < 50.0) {
             alerts.add(new DashboardOverviewResponse.Alert(
                     "WARNING",
                     String.format("평가 참여율이 낮습니다: %.1f%%",
                             evalStats.evaluationParticipationRate()),
                     LocalDateTime.now()
             ));
         }

        return alerts;
    }

    private AdminUserListResponse.UserSummary toUserSummary(User user) {
        int totalCalls = callRepository.countCompletedCallsByUser(user);
        int reportCount = (int) reportRepository.countByReportedUser(user);

        return new AdminUserListResponse.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getUserType().name(),
                user.getProvider(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                totalCalls,
                reportCount,
                user.isCurrentlySuspended()
        );
    }

    private ReportListResponse.ReportDetail toReportDetail(Report report) {
        ReportListResponse.UserInfo reporter = new ReportListResponse.UserInfo(
                report.getReporter().getId(),
                report.getReporter().getNickname(),
                report.getReporter().getEmail()
        );

        ReportListResponse.UserInfo reportedUser = new ReportListResponse.UserInfo(
                report.getReportedUser().getId(),
                report.getReportedUser().getNickname(),
                report.getReportedUser().getEmail()
        );

        Long callId = report.getCall() != null ? report.getCall().getId() : null;

        return new ReportListResponse.ReportDetail(
                report.getId(),
                reporter,
                reportedUser,
                callId,
                report.getReason().name(),
                report.getDetails(),
                report.getCreatedAt(),
                "BLOCKED" //고정값: 이미 차단됨
        );
    }

    private CallMonitoringResponse.ActiveCallInfo toActiveCallInfo(Call call) {
        int durationMinutes = (int) ChronoUnit.MINUTES.between(
                call.getStartAt(), LocalDateTime.now()
        );

        String recordingStatus = callRecordingRepository.findByCallId(call.getId())
                .map(recording -> recording.getRecordingStatus().name())
                .orElse("NOT_RECORDING");

        return new CallMonitoringResponse.ActiveCallInfo(
                call.getId(),
                call.getUser1().getNickname(),
                call.getUser2().getNickname(),
                call.getCategory().getName(),
                call.getStartAt(),
                durationMinutes,
                recordingStatus
        );
    }

    private CallMonitoringResponse.EndedCallInfo toEndedCallInfo(Call call) {
        int durationMinutes = call.getDurationSeconds() != null ?
                call.getDurationSeconds() / 60 : 0;

        boolean hadEvaluation = evaluationRepository.existsByCall(call);

        return new CallMonitoringResponse.EndedCallInfo(
                call.getId(),
                call.getUser1().getId(),
                call.getUser2().getId(),
                call.getCategory().getName(),
                call.getStartAt(),
                call.getEndAt(),
                durationMinutes,
                hadEvaluation
        );
    }

    private CallMonitoringResponse.CallStatistics getCallStatistics() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long totalToday = callRepository.countCompletedCallsBetween(
                todayStart, now, CallStatus.COMPLETED
        );

        Double avgDuration = callRepository.getAverageDurationMinutesBetween(todayStart, now);
        double averageDurationMinutes = avgDuration != null ?
                Math.round(avgDuration * 10.0) / 10.0 : 0.0; // 소수점 1자리

        double successRate = calculateSuccessRate(todayStart, now);

        int peakHour = calculatePeakHour(todayStart, now);

        log.debug("오늘 통화 통계 - 총: {}, 평균시간: {}분, 성공률: {}%, 피크: {}시",
                totalToday, averageDurationMinutes, successRate, peakHour);

        return new CallMonitoringResponse.CallStatistics(
                totalToday, averageDurationMinutes, successRate, peakHour
        );
    }

    private double calculateSuccessRate(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<Object[]> successRateData = matchingQueueRepository
                    .getMatchingSuccessRate(startDate, endDate);

            if (successRateData == null || successRateData.isEmpty()) {
                return 0.0;
            }

            Object[] data = successRateData.get(0);
            long matched = ((Number) data[0]).longValue();
            long total = ((Number) data[1]).longValue();

            if (total == 0) {
                return 0.0;
            }

            double rate = (double) matched / total * 100.0;
            return Math.round(rate * 10.0) / 10.0; // 소수점 1자리

        } catch (Exception e) {
            log.warn("매칭 성공률 계산 실패", e);
            return 0.0;
        }
    }

    private int calculatePeakHour(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<Object[]> hourlyData = callRepository.getCallCountByHour(startDate, endDate);

            if (hourlyData == null || hourlyData.isEmpty()) {
                return 20; // 기본값: 저녁 8시
            }

            // 첫 번째 결과가 가장 많은 통화 수를 가진 시간대
            Object[] peakData = hourlyData.get(0);
            return ((Number) peakData[0]).intValue();

        } catch (Exception e) {
            log.warn("피크 시간대 계산 실패", e);
            return 20; // 기본값
        }
    }

    private String mapSortField(String sortBy) {
        return switch (sortBy) {
            case "last_login" -> "lastLoginAt";
            case "report_count" -> "createdAt"; // 신고 수 정렬 미지원
            default -> "createdAt";
        };
    }
}