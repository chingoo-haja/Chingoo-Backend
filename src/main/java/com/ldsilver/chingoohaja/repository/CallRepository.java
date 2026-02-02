package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

    /**
     * 여러 카테고리의 통화 수를 한 번에 조회 (N+1 쿼리 방지)
     * @return [categoryId, callCount]
     */
    @Query("SELECT c.category.id, COUNT(c) FROM Call c " +
            "WHERE c.category.id IN :categoryIds " +
            "AND c.callStatus = 'COMPLETED' " +
            "AND c.createdAt BETWEEN :start AND :end " +
            "GROUP BY c.category.id")
    List<Object[]> countCallsByCategoryIdsBetween(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT AVG(c.durationSeconds) FROM Call c WHERE c.category.id = :categoryId " +
            "AND c.createdAt BETWEEN :start AND :end AND c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED")
    Double getAverageCallDurationByCategory(@Param("categoryId") Long categoryId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    // ========== Agora 관련 메서드들 ==========
    Optional<Call> findByAgoraChannelName(String agoraChannelName);

    @Query("SELECT c FROM Call c WHERE (c.user1.id = :userId OR c.user2.id = :userId) " +
            "AND c.callStatus IN (com.ldsilver.chingoohaja.domain.call.enums.CallStatus.READY, com.ldsilver.chingoohaja.domain.call.enums.CallStatus.IN_PROGRESS) ORDER BY c.createdAt DESC")
    List<Call> findActiveCallsByUserId(@Param("userId") Long userId);

    /**
     * 특정 기간 동안 사용자의 특정 상태 통화 수 조회
     */
    @Query("SELECT COUNT(c) FROM Call c WHERE (c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = :status " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    long countByUserAndStatusAndDateBetween(
            @Param("user") User user,
            @Param("status") CallStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 기간 동안 사용자의 총 통화 시간 (초)
     */
    @Query("SELECT COALESCE(SUM(c.durationSeconds), 0) FROM Call c " +
            "WHERE (c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = com.ldsilver.chingoohaja.domain.call.enums.CallStatus.COMPLETED " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    long sumDurationByUserAndDateBetween(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    /**
     * 특정 기간 동안 사용자의 특정 상태 통화 조회 (페이징)
     */
    @Query("SELECT c FROM Call c WHERE (c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = :status " +
            "AND c.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY c.createdAt DESC")
    Page<Call> findByUserAndStatusAndDateBetween(
            @Param("user") User user,
            @Param("status") CallStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // ======= 친구 관련 메서드들 =======
    /**
     * 두 사용자 간의 마지막 완료된 통화 조회
     * 친구 목록에서 마지막 통화 시간을 표시하기 위해 사용
     */
    @Query("SELECT c FROM Call c " +
            "WHERE ((c.user1.id = :userId1 AND c.user2.id = :userId2) " +
            "OR (c.user1.id = :userId2 AND c.user2.id = :userId1)) " +
            "AND c.callStatus = :status " +
            "ORDER BY c.endAt DESC")
    List<Call> findCallsBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("status") CallStatus status
    );

    /**
     * 두 사용자 간의 마지막 완료된 통화 조회 (단건)
     */
    default Optional<Call> findLastCompletedCallBetweenUsers(Long userId1, Long userId2) {
        List<Call> calls = findCallsBetweenUsers(userId1, userId2, CallStatus.COMPLETED);
        return calls.isEmpty() ? Optional.empty() : Optional.of(calls.get(0));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Call c WHERE c.id = :callId")
    Optional<Call> findByIdWithLock(@Param("callId") Long callId);

    /**
     * User, Category를 함께 조회하는 fetch join 메서드 (관리자용)
     * LazyInitializationException 방지 및 N+1 쿼리 문제 해결
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Call c " +
            "JOIN FETCH c.user1 " +
            "JOIN FETCH c.user2 " +
            "LEFT JOIN FETCH c.category " +
            "WHERE c.id = :callId")
    Optional<Call> findByIdWithLockAndFetchUsers(@Param("callId") Long callId);

    @Query("SELECT c FROM Call c WHERE c.callStatus = 'IN_PROGRESS' AND c.startAt < :threshold")
    List<Call> findStaleInProgressCalls(@Param("threshold") LocalDateTime threshold);

    List<Call> findByCallStatus(CallStatus callStatus);

    // 타임아웃 설정이 필요한 경우
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")}) // 3초
    @Query("SELECT c FROM Call c WHERE c.id = :callId")
    Optional<Call> findByIdWithLockAndTimeout(@Param("callId") Long callId);

    // 사용자별 활성 통화 조회 (락 필요)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Call c WHERE (c.user1.id = :userId OR c.user2.id = :userId) " +
            "AND c.callStatus IN (com.ldsilver.chingoohaja.domain.call.enums.CallStatus.READY, " +
            "com.ldsilver.chingoohaja.domain.call.enums.CallStatus.IN_PROGRESS) " +
            "ORDER BY c.createdAt DESC")
    List<Call> findActiveCallsByUserIdWithLock(@Param("userId") Long userId);


    // ======= 관리자 대시보드 관련 메서드들 =======

    int countByCallStatus(CallStatus callStatus);

    @Query("SELECT c FROM Call c WHERE c.callStatus = 'COMPLETED' " +
            "AND c.endAt >= :since ORDER BY c.endAt DESC")
    List<Call> findRecentEndedCalls(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(c) FROM Call c WHERE " +
            "(c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = 'COMPLETED'")
    int countCompletedCallsByUser(@Param("user") User user);

    @Query("SELECT AVG(c.durationSeconds) / 60.0 FROM Call c " +
            "WHERE c.callStatus = 'COMPLETED' " +
            "AND c.durationSeconds IS NOT NULL " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    Double getAverageDurationMinutesBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT HOUR(c.createdAt) as hour, COUNT(c) as count " +
            "FROM Call c " +
            "WHERE c.callStatus = 'COMPLETED' " +
            "AND c.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY HOUR(c.createdAt) " +
            "ORDER BY count DESC")
    List<Object[]> getCallCountByHour(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 특정 카테고리의 시간대별 통화 수 조회
     */
    @Query("SELECT HOUR(c.createdAt) as hour, COUNT(c) as count " +
            "FROM Call c " +
            "WHERE c.category.id = :categoryId " +
            "AND c.callStatus = 'COMPLETED' " +
            "AND c.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY HOUR(c.createdAt) " +
            "ORDER BY count DESC")
    List<Object[]> getCallCountByHourByCategory(
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(c) FROM Call c " +
            "WHERE c.endAt >= :since " +
            "AND c.callStatus = 'COMPLETED' " +
            "AND c.durationSeconds < :maxDuration")
    long countShortCallsSince(
            @Param("since") LocalDateTime since,
            @Param("maxDuration") int maxDuration
    );

    // ======= UserAnalytics 관련 메서드들 =======

    /**
     * Provider별 사용자들의 통화 성공률 조회 (사용자 참여 기준)
     * - 각 사용자가 참여한 통화를 해당 사용자의 provider로 집계
     * - user1과 user2가 다른 provider인 경우, 양쪽 provider 모두에 집계됨 (의도된 동작)
     * - 예: 카카오 사용자와 네이버 사용자의 통화 → 카카오 1건, 네이버 1건으로 집계
     * 성공 = COMPLETED, 실패 = CANCELLED 또는 FAILED (진행 중인 READY, IN_PROGRESS는 제외)
     */
    @Query("SELECT u.provider, " +
            "COUNT(CASE WHEN c.callStatus = 'COMPLETED' THEN 1 END) as completed, " +
            "COUNT(c) as total " +
            "FROM Call c " +
            "JOIN User u ON (c.user1.id = u.id OR c.user2.id = u.id) " +
            "WHERE c.callStatus IN ('COMPLETED', 'CANCELLED', 'FAILED') " +
            "AND c.createdAt BETWEEN :start AND :end " +
            "GROUP BY u.provider")
    List<Object[]> getSuccessRateByProvider(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Provider별 선호 카테고리 조회 (사용자 참여 기준)
     * - 각 사용자가 참여한 통화를 해당 사용자의 provider로 집계
     * - user1과 user2가 다른 provider인 경우, 양쪽 provider 모두에 집계됨 (의도된 동작)
     */
    @Query("SELECT u.provider, c.category.name, COUNT(c) as callCount " +
            "FROM Call c " +
            "JOIN User u ON (c.user1.id = u.id OR c.user2.id = u.id) " +
            "WHERE c.callStatus = 'COMPLETED' " +
            "AND c.createdAt BETWEEN :start AND :end " +
            "GROUP BY u.provider, c.category.name " +
            "ORDER BY u.provider, callCount DESC")
    List<Object[]> getPreferredCategoriesByProvider(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 특정 기간 동안 사용자당 평균 통화 수 조회
     */
    @Query(value = "SELECT AVG(call_count) FROM (" +
            "SELECT COUNT(*) as call_count " +
            "FROM calls c " +
            "JOIN users u ON (c.user1_id = u.id OR c.user2_id = u.id) " +
            "WHERE c.call_status = 'COMPLETED' " +
            "AND c.created_at BETWEEN :start AND :end " +
            "GROUP BY u.id" +
            ") as user_call_counts", nativeQuery = true)
    Double getAverageCallsPerUser(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 카테고리별 평균 대기시간 조회 (초 단위)
     * MatchingQueue 생성 시점(대기 시작)부터 Call 생성 시점(매칭 완료)까지의 시간 차이
     */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(SECOND, mq.created_at, c.created_at)) " +
            "FROM calls c " +
            "JOIN matching_queue mq ON mq.user_id = c.user1_id OR mq.user_id = c.user2_id " +
            "WHERE c.category_id = :categoryId " +
            "AND c.call_status = 'COMPLETED' " +
            "AND mq.queue_status = 'MATCHING' " +
            "AND c.created_at BETWEEN :start AND :end " +
            "AND mq.created_at <= c.created_at", nativeQuery = true)
    Double getAverageWaitTimeByCategory(
            @Param("categoryId") Long categoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
