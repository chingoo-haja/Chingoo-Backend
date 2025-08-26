package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.category.Category;
import com.ldsilver.chingoohaja.domain.matching.MatchingQueue;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueStatus;
import com.ldsilver.chingoohaja.domain.matching.enums.QueueType;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchingQueueRepository extends JpaRepository<MatchingQueue, Long> {
    // 특정 카테고리의 대기 중인 매칭 조회 (매칭 알고리즘용)
    @Query("SELECT mq FROM MatchingQueue mq WHERE mq.category = :category " +
            "AND mq.queueStatus = 'WAITING' AND mq.queueType = :queueType " +
            "ORDER BY mq.createdAt ASC")
    List<MatchingQueue> findWaitingQueuesByCategory(@Param("category") Category category,
                                                    @Param("queueType") QueueType queueType);

    // 가장 오래 대기 중인 매칭 조회
    @Query("SELECT mq FROM MatchingQueue mq WHERE mq.queueStatus = 'WAITING' " +
            "AND mq.queueType = :queueType ORDER BY mq.createdAt ASC")
    List<MatchingQueue> findOldestWaitingQueues(@Param("queueType") QueueType queueType);

    // 만료된 대기열 조회 (특정 시간 이전)
    @Query("SELECT mq FROM MatchingQueue mq WHERE mq.queueStatus = 'WAITING' " +
            "AND mq.createdAt < :expiredTime")
    List<MatchingQueue> findExpiredQueues(@Param("expiredTime") LocalDateTime expiredTime);

    // 대기열 상태 일괄 업데이트
    @Modifying
    @Query("UPDATE MatchingQueue mq SET mq.queueStatus = :newStatus " +
            "WHERE mq.queueStatus = 'WAITING' AND mq.createdAt < :expiredTime")
    int updateExpiredQueues(@Param("newStatus") QueueStatus newStatus,
                            @Param("expiredTime") LocalDateTime expiredTime);

    // 사용자의 매칭 이력 조회
    List<MatchingQueue> findByUserOrderByCreatedAtDesc(User user);

    // 현재 대기 중인 총 인원 수
    @Query("SELECT COUNT(mq) FROM MatchingQueue mq WHERE mq.queueStatus = 'WAITING'")
    long countWaitingQueues();

    // 카테고리별 대기 인원 수
    @Query("SELECT mq.category.name, COUNT(mq) FROM MatchingQueue mq " +
            "WHERE mq.queueStatus = 'WAITING' GROUP BY mq.category.name")
    List<Object[]> countWaitingQueuesByCategory();

    // 매칭 성공률 통계 (특정 기간)
    @Query("SELECT " +
            "COUNT(CASE WHEN mq.queueStatus = 'MATCHING' THEN 1 END) as matched, " +
            "COUNT(mq) as total " +
            "FROM MatchingQueue mq WHERE mq.createdAt BETWEEN :startDate AND :endDate")
    List<Object[]> getMatchingSuccessRate(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * 사용자의 대기 중인 매칭을 취소 상태로 변경
     */
    @Modifying
    @Transactional
    @Query("UPDATE MatchingQueue mq SET mq.queueStatus = 'CANCELLED' " +
            "WHERE mq.user = :user AND mq.queueStatus = 'WAITING'")
    int cancelUserWaitingQueue(@Param("user") User user);

    /**
     * 특정 매칭 큐의 상태를 취소로 변경
     */
    @Modifying
    @Transactional
    @Query("UPDATE MatchingQueue mq SET mq.queueStatus = 'CANCELLED' " +
            "WHERE mq.id = :id AND mq.queueStatus = 'WAITING'")
    int cancelMatchingQueueById(@Param("id") String id);

    @Modifying
    @Transactional
    @Query("UPDATE MatchingQueue mq SET mq.queueStatus = 'CANCELLED' " +
            "WHERE mq.queueId = :queueId AND mq.queueStatus = 'WAITING'")
    int cancelMatchingQueueByQueueId(@Param("queueId") String queueId);

    /**
     * queueId로 매칭 큐 조회
     */
    Optional<MatchingQueue> findByQueueId(String queueId);
}
