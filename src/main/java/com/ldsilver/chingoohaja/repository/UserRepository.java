package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    List<User> findByNicknameContaining(String pattern);

    // 특정 기간 가입자 조회 (통계용)
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    List<User> findUsersCreatedBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // 활성 유저 수 조회 (최근 30일 내 로그인)
    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN " +
            "(SELECT DISTINCT ut.user.id FROM UserToken ut WHERE ut.createdAt >= :thirtyDaysAgo)")
    long countActiveUsers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    Optional<User> findByEmailAndProvider(String email, String provider);
    Optional<User> findByProviderIdAndProvider(String providerId, String provider);
    long countByProvider(String provider);

    @Query("SELECT u.provider, COUNT(u) FROM User u GROUP BY u.provider ORDER BY COUNT(u) DESC")
    List<Object[]> getProviderStatistics();

    Optional<User> findByEmailAndProviderNot(String email, String excludeProvider);

    List<User> findByProviderNotAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(String provider, LocalDateTime since);

    Optional<User> findByNickname(String nickname);

}
