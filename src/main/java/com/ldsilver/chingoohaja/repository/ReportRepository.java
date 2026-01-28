package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.report.Report;
import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    /**
     * 특정 사용자가 특정 사용자를 이미 신고했는지 확인
     */
    boolean existsByReporterAndReportedUser(User reporter, User reportedUser);

    /**
     * 특정 사용자가 받은 신고 건수
     */
    long countByReportedUser(User reportedUser);

    /**
     * 특정 기간 동안 특정 사용자가 받은 신고 건수
     */
    @Query("SELECT COUNT(r) FROM Report r WHERE r.reportedUser = :user " +
            "AND r.createdAt BETWEEN :startDate AND :endDate")
    long countByReportedUserAndPeriod(@Param("user") User user,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * 특정 사용자가 받은 신고 목록 (관리자용)
     */
    List<Report> findByReportedUserOrderByCreatedAtDesc(User reportedUser);

    @Query("SELECT COUNT(r) FROM Report r " +
            "WHERE r.createdAt BETWEEN :startDate AND :endDate")
    long countByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

}
