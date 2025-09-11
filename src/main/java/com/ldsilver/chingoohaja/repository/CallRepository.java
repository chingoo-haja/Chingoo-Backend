package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {
    // 사용자별 통화 기록 조회 (페이징)
    @Query("SELECT c FROM Call c WHERE (c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = :status ORDER BY c.createdAt DESC")
    Page<Call> findByUserAndStatus(@Param("user") User user,
                                   @Param("status") CallStatus status,
                                   Pageable pageable);

    // 사용자별 모든 통화 기록 조회
    @Query("SELECT c FROM Call c WHERE c.user1 = :user OR c.user2 = :user " +
            "ORDER BY c.createdAt DESC")
    Page<Call> findByUser(@Param("user") User user, Pageable pageable);

    // 특정 기간 통화 통계
    @Query("SELECT COUNT(c) FROM Call c WHERE c.callStatus = :status " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    long countCompletedCallsBetween(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    @Param("status") CallStatus status);

    // 사용자별 통화 통계
    @Query("SELECT COUNT(c) FROM Call c WHERE (c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = :status")
    long countCompletedCallsByUser(@Param("user") User user,
                                   @Param("status") CallStatus status);

    // 사용자별 총 통화 시간
    @Query("SELECT COALESCE(SUM(c.durationSeconds), 0) FROM Call c " +
            "WHERE (c.user1 = :user OR c.user2 = :user) AND c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED")
    long sumDurationByUser(@Param("user") User user);

    // 카테고리별 통화 통계
    @Query("SELECT c.category.name, COUNT(c) FROM Call c " +
            "WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED AND c.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY c.category.name ORDER BY COUNT(c) DESC")
    List<Object[]> getCallStatsByCategory(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // 일일 통화 통계 (관리자용)
    @Query("SELECT DATE(c.createdAt), COUNT(c), AVG(c.durationSeconds) FROM Call c " +
            "WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED AND c.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(c.createdAt) ORDER BY DATE(c.createdAt)")
    List<Object[]> getDailyCallStats(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);


    // CallRepository에 추가 필요한 메서드들
    @Query("SELECT c.category.id, c.category.name, COUNT(c) FROM Call c " +
            "WHERE (c.user1.id = :userId OR c.user2.id = :userId) " +
            "GROUP BY c.category.id, c.category.name")
    List<Object[]> getUserCallStatsByCategory(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM Call c WHERE c.category.id = :categoryId " +
            "AND c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED " +
            "AND c.createdAt BETWEEN :start AND :end")
    long countCallsByCategoryBetween(@Param("categoryId") Long categoryId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("SELECT AVG(c.durationSeconds) FROM Call c WHERE c.category.id = :categoryId " +
            "AND c.createdAt BETWEEN :start AND :end AND c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED")
    Double getAverageCallDurationByCategory(@Param("categoryId") Long categoryId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    // ========== Agora 관련 메서드들 ==========
    Optional<Call> findByAgoraChannelName(String agoraChannelName);

    Optional<Call> findByAgoraResourceId(String agoraResourceId);

    Optional<Call> findByAgoraSid(String agoraSid);

    @Query("SELECT c FROM Call c WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.IN_PROGRESS ORDER BY c.startAt ASC")
    List<Call> findInProgressCalls();

    @Query("SELECT c FROM Call c WHERE c.recordingStartedAt IS NOT NULL AND c.recordingEndedAt IS NULL")
    List<Call> findRecordingCalls();

    @Query("SELECT c FROM Call c WHERE c.recordingFileUrl IS NOT NULL AND c.recordingFileUrl != ''")
    List<Call> findCallsWithRecording();

    @Query("SELECT c FROM Call c WHERE (c.user1.id = :userId OR c.user2.id = :userId) " +
            "AND c.callStatus IN (com.ldsilver.chingoohaja.domain.call.enums.CallStatus.READY, com.ldsilver.chingoohaja.domain.call.enums.CallStatus.IN_PROGRESS) ORDER BY c.createdAt DESC")
    List<Call> findActiveCallsByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Call c WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.IN_PROGRESS " +
            "AND c.startAt < :timeoutThreshold ORDER BY c.startAt ASC")
    List<Call> findLongRunningCalls(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    /**
     * 준비 상태에서 오래 머물러 있는 통화들 조회 (정리용)
     */
    @Query("SELECT c FROM Call c WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.READY " +
            "AND c.createdAt < :staleThreshold ORDER BY c.createdAt ASC")
    List<Call> findStaleCalls(@Param("staleThreshold") LocalDateTime staleThreshold);

    // ========== 통계 쿼리 확장 ==========

    /**
     * 녹음 통계 조회
     */
    @Query("SELECT " +
            "COUNT(CASE WHEN c.recordingFileUrl IS NOT NULL THEN 1 END) as recordedCalls, " +
            "COUNT(c) as totalCalls, " +
            "AVG(c.recordingDurationSeconds) as avgRecordingDuration " +
            "FROM Call c WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    List<Object[]> getRecordingStats(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    /**
     * 시간대별 통화 품질 통계 (녹음 지속시간 vs 실제 통화 시간)
     */
    @Query("SELECT " +
            "HOUR(c.startAt) as hour, " +
            "COUNT(c) as callCount, " +
            "AVG(c.durationSeconds) as avgCallDuration, " +
            "AVG(c.recordingDurationSeconds) as avgRecordingDuration " +
            "FROM Call c WHERE c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED " +
            "AND c.startAt BETWEEN :startDate AND :endDate " +
            "GROUP BY HOUR(c.startAt) ORDER BY hour")
    List<Object[]> getHourlyQualityStats(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    /**
     * Agora 채널 사용률 통계
     */
    @Query("SELECT " +
            "COUNT(DISTINCT c.agoraChannelName) as uniqueChannels, " +
            "COUNT(c) as totalCalls, " +
            "AVG(c.durationSeconds) as avgDuration " +
            "FROM Call c WHERE c.agoraChannelName IS NOT NULL " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    List<Object[]> getChannelUsageStats(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

}
