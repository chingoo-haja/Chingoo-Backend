package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByRefreshTokenAndIsActiveTrue(String refreshToken);
    List<UserToken> findByUserAndIsActiveTrueOrderByCreatedAtDesc(User user);

    // 사용자의 모든 토큰 비활성화
    @Modifying
    @Query("UPDATE UserToken ut SET ut.isActive = false WHERE ut.user = :user")
    void deactivateAllTokensByUser(@Param("user") User user);

    // 특정 토큰 비활성화
    @Modifying
    @Query("UPDATE UserToken ut SET ut.isActive = false WHERE ut.refreshToken = :refreshToken")
    void deactivateTokenByRefreshToken(@Param("refreshToken") String refreshToken);

    // 만료된 토큰 일괄 비활성화
    @Modifying
    @Query("UPDATE UserToken ut SET ut.isActive = false WHERE ut.expiresAt < :now AND ut.isActive = true")
    int deactivateExpiredTokens(@Param("now") LocalDateTime now);

    // 사용자별 토큰 개수 제한 (최신 N개만 유지)
    @Query("SELECT ut FROM UserToken ut WHERE ut.user = :user AND ut.isActive = true " +
            "ORDER BY ut.createdAt DESC")
    List<UserToken> findActiveTokensByUserOrderByCreatedAtDesc(@Param("user") User user);

}
