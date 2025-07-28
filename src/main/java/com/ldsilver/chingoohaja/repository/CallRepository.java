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
    @Query("SELECT COUNT(c) FROM Call c WHERE c.callStatus = 'COMPLETED' " +
            "AND c.createdAt BETWEEN :startDate AND :endDate")
    long countCompletedCallsBetween(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    // 사용자별 통화 통계
    @Query("SELECT COUNT(c) FROM Call c WHERE (c.user1 = :user OR c.user2 = :user) " +
            "AND c.callStatus = 'COMPLETED'")
    long countCompletedCallsByUser(@Param("user") User user);

    // 사용자별 총 통화 시간
    @Query("SELECT COALESCE(SUM(c.durationSeconds), 0) FROM Call c " +
            "WHERE (c.user1 = :user OR c.user2 = :user) AND c.callStatus = 'COMPLETED'")
    long sumDurationByUser(@Param("user") User user);

    // 카테고리별 통화 통계
    @Query("SELECT c.category.name, COUNT(c) FROM Call c " +
            "WHERE c.callStatus = 'COMPLETED' AND c.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY c.category.name ORDER BY COUNT(c) DESC")
    List<Object[]> getCallStatsByCategory(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // 일일 통화 통계 (관리자용)
    @Query("SELECT DATE(c.createdAt), COUNT(c), AVG(c.durationSeconds) FROM Call c " +
            "WHERE c.callStatus = 'COMPLETED' AND c.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(c.createdAt) ORDER BY DATE(c.createdAt)")
    List<Object[]> getDailyCallStats(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

}
