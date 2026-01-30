package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.call.Call;
import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    // 사용자의 평균 평점 계산 (긍정 평가 비율)
    @Query("SELECT " +
            "CASE WHEN COUNT(e) = 0 THEN NULL ELSE "+
            "COUNT(CASE WHEN e.feedbackType = 'POSITIVE' THEN 1 END) * 100.0 / COUNT(e) END " +
            "FROM Evaluation e WHERE e.evaluated = :user")
    Double getPositiveFeedbackPercentageByUser(@Param("user") User user);

    // 특정 기간 평가 통계
    @Query("SELECT e.feedbackType, COUNT(e) FROM Evaluation e " +
            "WHERE e.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY e.feedbackType")
    List<Object[]> getFeedbackStatsBetween(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    // 신고(부정 평가) 많은 사용자 조회
    @Query("SELECT e.evaluated, COUNT(e) as reportCount FROM Evaluation e " +
            "WHERE e.feedbackType = 'NEGATIVE' " +
            "GROUP BY e.evaluated " +
            "HAVING COUNT(e) >= :threshold " +
            "ORDER BY COUNT(e) DESC")
    List<Object[]> getUsersWithManyReports(@Param("threshold") long threshold);

    // 중복 평가 검증용 - 특정 통화에서 특정 평가자가 특정 대상을 평가했는지 확인
    Optional<Evaluation> findByCallAndEvaluatorAndEvaluated(Call call, User evaluator, User evaluated);

    // 통화별 평가 조회
    List<Evaluation> findByCallOrderByCreatedAtDesc(Call call);

    // 사용자가 받은 평가 조회 (페이징 가능)
    List<Evaluation> findByEvaluatedOrderByCreatedAtDesc(User evaluated);

    // 사용자가 한 평가 조회 (페이징 가능)
    List<Evaluation> findByEvaluatorOrderByCreatedAtDesc(User evaluator);

    // 특정 사용자의 이번 달 평가 통계
    @Query("SELECT e.feedbackType, COUNT(e) FROM Evaluation e " +
            "WHERE e.evaluated = :user " +
            "AND e.createdAt >= :monthStart AND e.createdAt < :monthEnd " +
            "GROUP BY e.feedbackType")
    List<Object[]> getUserMonthlyStats(@Param("user") User user,
                                       @Param("monthStart") LocalDateTime monthStart,
                                       @Param("monthEnd") LocalDateTime monthEnd);

    // 모든 사용자의 긍정 평가율 조회 (순위 계산용)
    @Query("SELECT e.evaluated.id, " +
            "CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE " +
            "COUNT(CASE WHEN e.feedbackType = 'POSITIVE' THEN 1 END) * 100.0 / COUNT(e) END " +
            "FROM Evaluation e " +
            "GROUP BY e.evaluated.id " +
            "HAVING COUNT(e) > 0 " +
            "ORDER BY (COUNT(CASE WHEN e.feedbackType = 'POSITIVE' THEN 1 END) * 100.0 / COUNT(e)) DESC")
    List<Object[]> getAllUsersPositiveRates();


    // 이번 달 상위 10% 사용자 조회 (뱃지 지급용)
    @Query("SELECT e.evaluated.id, " +
            "SUM(CASE WHEN e.feedbackType = 'POSITIVE' THEN 1 ELSE 0 END) * 100.0 / COUNT(e), " +
            "COUNT(e) as totalCount " +
            "FROM Evaluation e " +
            "WHERE e.createdAt >= :monthStart AND e.createdAt < :monthEnd " +
            "GROUP BY e.evaluated.id " +
            "HAVING COUNT(e) >= :minEvaluations " +
            "ORDER BY (COUNT(CASE WHEN e.feedbackType = 'POSITIVE' THEN 1 END) * 100.0 / COUNT(e)) DESC")
    List<Object[]> getTopPerformersOfMonth(@Param("monthStart") LocalDateTime monthStart,
                                           @Param("monthEnd") LocalDateTime monthEnd,
                                           @Param("minEvaluations") long minEvaluations);

    // 특정 기간 동안의 총 평가 수
    @Query("SELECT COUNT(e) FROM Evaluation e " +
            "WHERE e.createdAt BETWEEN :startDate AND :endDate")
    long countEvaluationsBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // 특정 기간 동안의 피드백 타입별 개수
    @Query("SELECT e.feedbackType, COUNT(e) FROM Evaluation e " +
            "WHERE e.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY e.feedbackType")
    List<Object[]> countByFeedbackTypeBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // 특정 Call에 대한 평가 존재 여부
    boolean existsByCall(Call call);

    /**
     * 카테고리별 사용자 만족도 조회 (긍정 평가 비율, 0~5 스케일로 변환)
     * - 긍정 평가 비율을 0~100%에서 0~5 스케일로 변환
     * - 예: 80% 긍정 → 4.0점
     */
    @Query("SELECT " +
            "CASE WHEN COUNT(e) = 0 THEN NULL ELSE " +
            "(COUNT(CASE WHEN e.feedbackType = 'POSITIVE' THEN 1 END) * 5.0 / COUNT(e)) END " +
            "FROM Evaluation e " +
            "WHERE e.call.category.id = :categoryId " +
            "AND e.createdAt BETWEEN :start AND :end")
    Double getAverageSatisfactionByCategory(
            @Param("categoryId") Long categoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
