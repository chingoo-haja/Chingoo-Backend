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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("UserRepository 테스트")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    private User kakaoUser;
    private User googleUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        userTokenRepository.deleteAll();
        userRepository.deleteAll();

        kakaoUser = userRepository.save(User.of("kakao@test.com", "카카오닉", "카카오사용자",
                Gender.MALE, LocalDate.of(1990, 1, 1), "01012345678",
                UserType.USER, null, "kakao", "k1"));
        googleUser = userRepository.save(User.of("google@test.com", "구글닉", "구글사용자",
                Gender.FEMALE, LocalDate.of(1995, 6, 15), "01087654321",
                UserType.USER, null, "google", "g1"));
        adminUser = userRepository.save(User.of("admin@test.com", "관리자닉", "관리자",
                Gender.MALE, LocalDate.of(1985, 3, 20), "01011112222",
                UserType.ADMIN, null, "kakao", "k2"));
    }

    @Nested
    @DisplayName("existsByEmail / existsByNickname")
    class ExistsByEmailAndNickname {

        @Test
        @DisplayName("존재하는 이메일이면 true를 반환한다")
        void givenExistingEmail_whenCheck_thenReturnsTrue() {
            assertThat(userRepository.existsByEmail("kakao@test.com")).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 false를 반환한다")
        void givenNonExistingEmail_whenCheck_thenReturnsFalse() {
            assertThat(userRepository.existsByEmail("none@test.com")).isFalse();
        }

        @Test
        @DisplayName("존재하는 닉네임이면 true를 반환한다")
        void givenExistingNickname_whenCheck_thenReturnsTrue() {
            assertThat(userRepository.existsByNickname("카카오닉")).isTrue();
        }
    }

    @Nested
    @DisplayName("findByNicknameContaining")
    class FindByNicknameContaining {

        @Test
        @DisplayName("닉네임 패턴으로 사용자를 검색한다")
        void givenPattern_whenFind_thenReturnsMatchingUsers() {
            List<User> result = userRepository.findByNicknameContaining("닉");

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("매칭되지 않으면 빈 리스트를 반환한다")
        void givenNoMatch_whenFind_thenReturnsEmpty() {
            List<User> result = userRepository.findByNicknameContaining("네이버");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findUsersCreatedBetween")
    class FindUsersCreatedBetween {

        @Test
        @DisplayName("특정 기간 내 가입한 사용자를 반환한다")
        void givenDateRange_whenFind_thenReturnsUsersInRange() {
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            List<User> result = userRepository.findUsersCreatedBetween(start, end);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("기간 외 사용자는 반환하지 않는다")
        void givenPastRange_whenFind_thenReturnsEmpty() {
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now().minusDays(29);

            List<User> result = userRepository.findUsersCreatedBetween(start, end);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countUsersCreatedBetween")
    class CountUsersCreatedBetween {

        @Test
        @DisplayName("기간 내 가입자 수를 반환한다")
        void givenDateRange_whenCount_thenReturnsCount() {
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            long count = userRepository.countUsersCreatedBetween(start, end);

            assertThat(count).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("countActiveUsers")
    class CountActiveUsers {

        @Test
        @DisplayName("최근 토큰이 있는 활성 사용자 수를 반환한다")
        void givenActiveTokens_whenCount_thenReturnsActiveCount() {
            // given - kakaoUser에 토큰 생성
            userTokenRepository.save(UserToken.of(kakaoUser, "refresh-token",
                    LocalDateTime.now().plusDays(30), "device1", true));

            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            // when
            long count = userRepository.countActiveUsers(thirtyDaysAgo);

            // then
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("토큰이 없으면 0을 반환한다")
        void givenNoTokens_whenCount_thenReturnsZero() {
            long count = userRepository.countActiveUsers(LocalDateTime.now().minusDays(30));

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findByEmailAndProvider")
    class FindByEmailAndProvider {

        @Test
        @DisplayName("이메일과 프로바이더로 사용자를 조회한다")
        void givenEmailAndProvider_whenFind_thenReturnsUser() {
            Optional<User> result = userRepository.findByEmailAndProvider("kakao@test.com", "kakao");

            assertThat(result).isPresent();
            assertThat(result.get().getNickname()).isEqualTo("카카오닉");
        }

        @Test
        @DisplayName("프로바이더가 다르면 조회되지 않는다")
        void givenWrongProvider_whenFind_thenReturnsEmpty() {
            Optional<User> result = userRepository.findByEmailAndProvider("kakao@test.com", "google");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProviderStatistics")
    class GetProviderStatistics {

        @Test
        @DisplayName("프로바이더별 사용자 통계를 반환한다")
        void whenGetStats_thenReturnsProviderCounts() {
            List<Object[]> result = userRepository.getProviderStatistics();

            assertThat(result).hasSize(2); // kakao(2), google(1)
            // kakao가 2명으로 가장 많으므로 첫 번째
            assertThat(result.get(0)[0]).isEqualTo("kakao");
            assertThat(result.get(0)[1]).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("findBySearchAndUserType")
    class FindBySearchAndUserType {

        @Test
        @DisplayName("검색어와 유저 타입으로 사용자를 검색한다")
        void givenSearchAndType_whenFind_thenReturnsFiltered() {
            Page<User> result = userRepository.findBySearchAndUserType(
                    "kakao", UserType.USER, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("kakao@test.com");
        }

        @Test
        @DisplayName("검색어가 null이면 유저 타입으로만 필터링한다")
        void givenNullSearch_whenFind_thenFiltersByTypeOnly() {
            Page<User> result = userRepository.findBySearchAndUserType(
                    null, UserType.USER, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("유저 타입이 null이면 검색어로만 필터링한다")
        void givenNullType_whenFind_thenFiltersBySearchOnly() {
            Page<User> result = userRepository.findBySearchAndUserType(
                    "관리자", null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("닉네임으로도 검색이 가능하다")
        void givenNicknameSearch_whenFind_thenReturnsMatching() {
            Page<User> result = userRepository.findBySearchAndUserType(
                    "구글닉", null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("google@test.com");
        }
    }

    @Nested
    @DisplayName("countByProvider")
    class CountByProvider {

        @Test
        @DisplayName("프로바이더별 사용자 수를 반환한다")
        void givenProvider_whenCount_thenReturnsCount() {
            assertThat(userRepository.countByProvider("kakao")).isEqualTo(2);
            assertThat(userRepository.countByProvider("google")).isEqualTo(1);
            assertThat(userRepository.countByProvider("naver")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findByNickname / findByPhoneNumber")
    class FindByUniqueFields {

        @Test
        @DisplayName("닉네임으로 사용자를 조회한다")
        void givenNickname_whenFind_thenReturnsUser() {
            Optional<User> result = userRepository.findByNickname("카카오닉");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("전화번호로 사용자를 조회한다")
        void givenPhoneNumber_whenFind_thenReturnsUser() {
            Optional<User> result = userRepository.findByPhoneNumber("01012345678");
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("kakao@test.com");
        }
    }
}
