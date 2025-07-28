package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.evaluation.Evaluation;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    // 사용자의 평균 평점 계산 (긍정 평가 비율)
    @Query("SELECT " +
            "CASE WHEN COUNT(e) = 0 THEN 0.0 ELSE "+
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

}
