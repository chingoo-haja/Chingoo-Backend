package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.CallStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallStatisticsRepository extends JpaRepository<CallStatistics, Long> {

    /**
     * 특정 통화의 모든 통계 조회
     */
    List<CallStatistics> findByCallId(Long callId);

    /**
     * 특정 사용자의 통화별 통계 조회
     */
    Optional<CallStatistics> findByCallIdAndUserId(Long callId, Long userId);

    /**
     * 특정 사용자의 총 통화 시간 (초)
     */
    @Query("SELECT COALESCE(SUM(cs.durationSeconds), 0) FROM CallStatistics cs " +
            "WHERE cs.user.id = :userId")
    Long getTotalDurationByUserId(@Param("userId") Long userId);

    /**
     * 특정 사용자의 평균 통화 시간 (초)
     */
    @Query("SELECT COALESCE(AVG(cs.durationSeconds), 0.0) FROM CallStatistics cs " +
            "WHERE cs.user.id = :userId")
    Double getAverageDurationByUserId(@Param("userId") Long userId);

    /**
     * 특정 사용자의 총 데이터 사용량 (bytes)
     */
    @Query("SELECT COALESCE(SUM(cs.totalBytes), 0) FROM CallStatistics cs " +
            "WHERE cs.user.id = :userId")
    Long getTotalDataUsageByUserId(@Param("userId") Long userId);

    /**
     * 특정 사용자의 평균 네트워크 품질
     */
    @Query("SELECT COALESCE(AVG(cs.averageNetworkQuality), 0.0) FROM CallStatistics cs " +
            "WHERE cs.user.id = :userId")
    Double getAverageNetworkQualityByUserId(@Param("userId") Long userId);

    /**
     * 기간별 통화 통계 조회
     */
    @Query("SELECT cs FROM CallStatistics cs " +
            "WHERE cs.user.id = :userId " +
            "AND cs.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY cs.createdAt DESC")
    List<CallStatistics> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 카테고리별 평균 통화 시간
     */
    @Query("SELECT c.category.id, AVG(cs.durationSeconds) " +
            "FROM CallStatistics cs " +
            "JOIN cs.call c " +
            "WHERE cs.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY c.category.id")
    List<Object[]> getAverageDurationByCategory(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
