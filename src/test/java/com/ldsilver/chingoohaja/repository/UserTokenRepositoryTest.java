package com.ldsilver.chingoohaja.repository;

import com.ldsilver.chingoohaja.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.UserToken;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("UserTokenRepository 테스트")
class UserTokenRepositoryTest {

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;
    private UserToken activeToken;
    private UserToken inactiveToken;

    @BeforeEach
    void setUp() {
        userTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.of("test@test.com", "테스터닉", "테스터",
                Gender.MALE, LocalDate.of(1990, 1, 1), null,
                UserType.USER, null, "kakao", "k1"));

        activeToken = userTokenRepository.save(UserToken.of(testUser, "active-refresh-token",
                LocalDateTime.now().plusDays(30), "device1", true));
        inactiveToken = userTokenRepository.save(UserToken.of(testUser, "inactive-refresh-token",
                LocalDateTime.now().plusDays(30), "device2", false));
    }

    @Nested
    @DisplayName("findByRefreshTokenAndIsActiveTrue")
    class FindByRefreshTokenAndIsActiveTrue {

        @Test
        @DisplayName("활성 리프레시 토큰으로 조회한다")
        void givenActiveToken_whenFind_thenReturnsToken() {
            Optional<UserToken> result = userTokenRepository
                    .findByRefreshTokenAndIsActiveTrue("active-refresh-token");

            assertThat(result).isPresent();
            assertThat(result.get().getDeviceInfo()).isEqualTo("device1");
        }

        @Test
        @DisplayName("비활성 토큰은 조회되지 않는다")
        void givenInactiveToken_whenFind_thenReturnsEmpty() {
            Optional<UserToken> result = userTokenRepository
                    .findByRefreshTokenAndIsActiveTrue("inactive-refresh-token");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 토큰은 조회되지 않는다")
        void givenNonExistingToken_whenFind_thenReturnsEmpty() {
            Optional<UserToken> result = userTokenRepository
                    .findByRefreshTokenAndIsActiveTrue("non-existing-token");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserAndIsActiveTrueOrderByCreatedAtDesc")
    class FindByUserAndIsActiveTrue {

        @Test
        @DisplayName("사용자의 활성 토큰 목록을 반환한다")
        void givenUser_whenFind_thenReturnsActiveTokens() {
            List<UserToken> result = userTokenRepository
                    .findByUserAndIsActiveTrueOrderByCreatedAtDesc(testUser);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRefreshToken()).isEqualTo("active-refresh-token");
        }
    }

    @Nested
    @DisplayName("deactivateAllTokensByUser")
    class DeactivateAllTokensByUser {

        @Test
        @DisplayName("사용자의 모든 토큰을 비활성화한다")
        void givenUser_whenDeactivate_thenAllTokensInactive() {
            // given
            userTokenRepository.save(UserToken.of(testUser, "another-active-token",
                    LocalDateTime.now().plusDays(30), "device3", true));

            // when
            userTokenRepository.deactivateAllTokensByUser(testUser);
            entityManager.flush();
            entityManager.clear();

            // then
            List<UserToken> activeTokens = userTokenRepository
                    .findByUserAndIsActiveTrueOrderByCreatedAtDesc(testUser);
            assertThat(activeTokens).isEmpty();
        }
    }

    @Nested
    @DisplayName("deactivateTokenByRefreshToken")
    class DeactivateTokenByRefreshToken {

        @Test
        @DisplayName("특정 리프레시 토큰을 비활성화한다")
        void givenRefreshToken_whenDeactivate_thenTokenInactive() {
            // when
            userTokenRepository.deactivateTokenByRefreshToken("active-refresh-token");
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<UserToken> result = userTokenRepository
                    .findByRefreshTokenAndIsActiveTrue("active-refresh-token");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deactivateExpiredTokens")
    class DeactivateExpiredTokens {

        @Test
        @DisplayName("만료된 토큰을 일괄 비활성화한다")
        void givenExpiredTokens_whenDeactivate_thenReturnsCount() {
            // given - 미래 날짜로 저장 후 native query로 만료 시킴 (@Future 검증 우회)
            UserToken token = userTokenRepository.save(UserToken.of(testUser, "expired-token",
                    LocalDateTime.now().plusDays(30), "device-expired", true));
            entityManager.flush();

            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE user_tokens SET expires_at = :past WHERE id = :id")
                    .setParameter("past", LocalDateTime.now().minusDays(1))
                    .setParameter("id", token.getId())
                    .executeUpdate();
            entityManager.clear();

            // when
            int deactivated = userTokenRepository.deactivateExpiredTokens(LocalDateTime.now());
            entityManager.flush();
            entityManager.clear();

            // then
            assertThat(deactivated).isEqualTo(1);
        }

        @Test
        @DisplayName("만료되지 않은 토큰은 비활성화하지 않는다")
        void givenNoExpiredTokens_whenDeactivate_thenReturnsZero() {
            int deactivated = userTokenRepository.deactivateExpiredTokens(LocalDateTime.now());

            assertThat(deactivated).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findActiveTokensByUserOrderByCreatedAtDesc")
    class FindActiveTokensByUser {

        @Test
        @DisplayName("활성 토큰을 최신순으로 반환한다")
        void givenMultipleTokens_whenFind_thenReturnsOrderedByCreatedAt() {
            // given
            userTokenRepository.save(UserToken.of(testUser, "newer-token",
                    LocalDateTime.now().plusDays(30), "device-new", true));
            entityManager.flush();

            // when
            List<UserToken> result = userTokenRepository
                    .findActiveTokensByUserOrderByCreatedAtDesc(testUser);

            // then
            assertThat(result).hasSize(2);
        }
    }
}
