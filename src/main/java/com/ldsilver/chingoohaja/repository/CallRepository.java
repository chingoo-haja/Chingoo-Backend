package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.call.enums.CallStatus;
import com.ldsilver.chingoohaja.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("SELECT c FROM Call c WHERE c.callStatus = 'IN_PROGRESS' AND c.startAt < :threshold")
    List<Call> findStaleInProgressCalls(@Param("threshold") LocalDateTime threshold);

}
