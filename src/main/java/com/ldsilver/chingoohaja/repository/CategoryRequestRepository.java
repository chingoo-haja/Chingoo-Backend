package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.category.DesiredCategory;
import com.ldsilver.chingoohaja.domain.category.enums.RequestStatus;
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
public interface CategoryRequestRepository extends JpaRepository<DesiredCategory, Long> {

    // 사용자별 요청 조회
    List<DesiredCategory> findByUserOrderByCreatedAtDesc(User user);

    // 상태별 요청 조회 (관리자용)
    Page<DesiredCategory> findByStatusOrderByCreatedAtDesc(
            RequestStatus status, Pageable pageable);

    // 전체 요청 조회 (관리자용)
    Page<DesiredCategory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 중복 체크 (같은 사용자가 같은 카테고리를 최근에 요청했는지)
    @Query("SELECT COUNT(cr) > 0 FROM DesiredCategory cr " +
            "WHERE cr.user = :user AND cr.categoryName = :categoryName " +
            "AND cr.createdAt > :since")
    boolean existsByUserAndCategoryNameAndCreatedAtAfter(
            @Param("user") User user,
            @Param("categoryName") String categoryName,
            @Param("since") LocalDateTime since
    );
}