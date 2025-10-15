package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.CallStatistics;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.dto.call.request.CallStatisticsRequest;
import com.ldsilver.chingoohaja.repository.CallRepository;
import com.ldsilver.chingoohaja.repository.CallStatisticsRepository;
import com.ldsilver.chingoohaja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallStatisticsService {

    private final CallStatisticsRepository callStatisticsRepository;
    private final CallRepository callRepository;
    private final UserRepository userRepository;

    /**
     * 통화 통계 저장
     * - 프론트엔드에서 전송한 Agora 통계를 DB에 저장
     * - 중복 저장 방지 (이미 저장된 경우 무시)
     */
    @Transactional
    public void saveCallStatistics(Long userId, Long callId, CallStatisticsRequest request) {
        log.debug("통화 통계 저장 시작 - userId: {}, callId: {}", userId, callId);

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2. Call 조회 및 권한 검증
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALL_NOT_FOUND));

        if (!call.isParticipant(userId)) {
            log.warn("통화 통계 저장 권한 없음 - userId: {}, callId: {}", userId, callId);
            throw new CustomException(ErrorCode.CALL_NOT_PARTICIPANT);
        }

        // 3. 통계 저장
        try {
            CallStatistics statistics = CallStatistics.from(call, user, request);
            callStatisticsRepository.save(statistics);
            log.info("통화 통계 저장 완료 - userId: {}, callId: {}, duration: {}초", userId, callId, request.duration());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateStatistics(e)) {
                log.info("이미 저장된 통화 통계 - userId: {}, callId: {}", userId, callId);
                return;
            }
            throw e;
        }

    }

    private boolean isDuplicateStatistics(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException violationException) {
            String constraintName = violationException.getConstraintName();
            return "23000".equals(violationException.getSQLState());
        }
        return false;
    }

    /**
     * 사용자별 통계 요약 조회 (향후 대시보드용)
     */
    @Transactional(readOnly = true)
    public UserStatisticsSummary getUserStatisticsSummary(Long userId) {
        log.debug("사용자 통계 요약 조회 - userId: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        Long totalDuration = callStatisticsRepository.getTotalDurationByUserId(userId);
        Double avgDuration = callStatisticsRepository.getAverageDurationByUserId(userId);
        Long totalDataUsage = callStatisticsRepository.getTotalDataUsageByUserId(userId);
        Double avgNetworkQuality = callStatisticsRepository.getAverageNetworkQualityByUserId(userId);

        return new UserStatisticsSummary(
                userId,
                totalDuration,
                avgDuration,
                totalDataUsage,
                avgNetworkQuality
        );
    }

    public record UserStatisticsSummary(
            Long userId,
            Long totalDurationSeconds,
            Double averageDurationSeconds,
            Long totalDataUsageBytes,
            Double averageNetworkQuality
    ) {
        public double getTotalDataUsageMB() {
            return totalDataUsageBytes != null ? totalDataUsageBytes / (1024.0 * 1024.0) : 0.0;
        }

        public String getNetworkQualityDescription() {
            if (averageNetworkQuality == null) return "UNKNOWN";
            if (averageNetworkQuality <= 1.5) return "EXCELLENT";
            if (averageNetworkQuality <= 2.5) return "GOOD";
            if (averageNetworkQuality <= 3.5) return "POOR";
            if (averageNetworkQuality <= 4.5) return "BAD";
            if (averageNetworkQuality <= 5.5) return "VERY_BAD";
            return "DOWN";
        }
    }
}
