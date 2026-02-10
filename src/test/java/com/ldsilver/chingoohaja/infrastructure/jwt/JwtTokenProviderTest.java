package com.ldsilver.chingoohaja.infrastructure.jwt;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider 테스트")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    private static final String SECRET = "test-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-usage";
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@example.com";
    private static final String USER_TYPE = "USER";

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setAccessTokenExpiration(3600000L); // 1시간
        jwtProperties.setRefreshTokenExpiration(2592000000L); // 30일
        jwtProperties.setIssuer("chingoo-haja");
        jwtProperties.setAccessTokenSubject("access_token");
        jwtProperties.setRefreshTokenSubject("refresh_token");

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("유효한 액세스 토큰을 생성한다")
        void givenValidParams_whenGenerate_thenReturnsAccessToken() {
            // when
            String token = jwtTokenProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // JWT 형식: header.payload.signature
        }

        @Test
        @DisplayName("생성된 액세스 토큰에서 클레임을 추출할 수 있다")
        void givenGeneratedToken_whenExtractClaims_thenReturnsCorrectValues() {
            // given
            String token = jwtTokenProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when & then
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo(EMAIL);
            assertThat(jwtTokenProvider.getUserTypeFromToken(token)).isEqualTo(USER_TYPE);
            assertThat(jwtTokenProvider.getTokenType(token)).isEqualTo("ACCESS");
        }

        @Test
        @DisplayName("생성된 액세스 토큰은 ACCESS 타입이다")
        void givenGeneratedToken_whenCheckType_thenIsAccessToken() {
            // given
            String token = jwtTokenProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when & then
            assertThat(jwtTokenProvider.isAccessToken(token)).isTrue();
            assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("유효한 리프레시 토큰을 생성한다")
        void givenUserId_whenGenerate_thenReturnsRefreshToken() {
            // when
            String token = jwtTokenProvider.generateRefreshToken(USER_ID);

            // then
            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.getTokenType(token)).isEqualTo("REFRESH");
        }

        @Test
        @DisplayName("생성된 리프레시 토큰은 REFRESH 타입이다")
        void givenGeneratedToken_whenCheckType_thenIsRefreshToken() {
            // given
            String token = jwtTokenProvider.generateRefreshToken(USER_ID);

            // when & then
            assertThat(jwtTokenProvider.isRefreshToken(token)).isTrue();
            assertThat(jwtTokenProvider.isAccessToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("유효한 토큰이면 true를 반환한다")
        void givenValidToken_whenValidate_thenReturnsTrue() {
            // given
            String token = jwtTokenProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when & then
            assertThat(jwtTokenProvider.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("만료된 토큰이면 예외를 던진다")
        void givenExpiredToken_whenValidate_thenThrowsException() {
            // given - 만료 시간을 0으로 설정
            JwtProperties expiredProps = new JwtProperties();
            expiredProps.setSecret(SECRET);
            expiredProps.setAccessTokenExpiration(0L);
            expiredProps.setIssuer("chingoo-haja");
            expiredProps.setAccessTokenSubject("access_token");

            JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProps);
            expiredProvider.init();

            String token = expiredProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when & then
            assertThatThrownBy(() -> expiredProvider.isTokenValid(token))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.JWT_EXPIRED));
        }

        @Test
        @DisplayName("잘못된 형식의 토큰이면 false를 반환한다")
        void givenMalformedToken_whenValidate_thenReturnsFalse() {
            // given
            String malformedToken = "not.a.valid.jwt.token";

            // when & then - CustomException(JWT_SIGNATURE_INVALID) 또는 false
            try {
                boolean result = jwtTokenProvider.isTokenValid(malformedToken);
                assertThat(result).isFalse();
            } catch (CustomException e) {
                assertThat(e.getErrorCode()).isIn(
                        ErrorCode.JWT_SIGNATURE_INVALID,
                        ErrorCode.JWT_INVALID
                );
            }
        }

        @Test
        @DisplayName("다른 시크릿으로 서명된 토큰이면 예외를 던진다")
        void givenTokenWithDifferentSecret_whenValidate_thenThrowsException() {
            // given - 다른 시크릿으로 토큰 생성
            JwtProperties otherProps = new JwtProperties();
            otherProps.setSecret("another-secret-key-that-is-at-least-64-bytes-long-for-hs512-algorithm-xxxx");
            otherProps.setAccessTokenExpiration(3600000L);
            otherProps.setIssuer("chingoo-haja");
            otherProps.setAccessTokenSubject("access_token");

            JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);
            otherProvider.init();

            String token = otherProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when & then
            assertThatThrownBy(() -> jwtTokenProvider.isTokenValid(token))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isIn(ErrorCode.JWT_SIGNATURE_INVALID, ErrorCode.JWT_INVALID));
        }
    }

    @Nested
    @DisplayName("getExpirationFromToken")
    class GetExpirationFromToken {

        @Test
        @DisplayName("토큰의 만료 시간을 LocalDateTime으로 반환한다")
        void givenValidToken_whenGetExpiration_thenReturnsLocalDateTime() {
            // given
            String token = jwtTokenProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when
            LocalDateTime expiration = jwtTokenProvider.getExpirationFromToken(token);

            // then
            assertThat(expiration).isAfter(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("extractTokenFromBearer")
    class ExtractTokenFromBearer {

        @Test
        @DisplayName("Bearer 접두사가 있으면 토큰을 추출한다")
        void givenBearerToken_whenExtract_thenReturnsToken() {
            // when
            String result = jwtTokenProvider.extractTokenFromBearer("Bearer some.jwt.token");

            // then
            assertThat(result).isEqualTo("some.jwt.token");
        }

        @Test
        @DisplayName("Bearer 접두사가 없으면 null을 반환한다")
        void givenNonBearerToken_whenExtract_thenReturnsNull() {
            // when & then
            assertThat(jwtTokenProvider.extractTokenFromBearer("Basic abc")).isNull();
            assertThat(jwtTokenProvider.extractTokenFromBearer(null)).isNull();
        }
    }

    @Nested
    @DisplayName("getTimeUntilExpiration")
    class GetTimeUntilExpiration {

        @Test
        @DisplayName("만료까지 남은 시간을 밀리초로 반환한다")
        void givenValidToken_whenGetTimeUntilExpiration_thenReturnsPositive() {
            // given
            String token = jwtTokenProvider.generateAccessToken(USER_ID, EMAIL, USER_TYPE);

            // when
            long timeUntilExpiration = jwtTokenProvider.getTimeUntilExpiration(token);

            // then
            assertThat(timeUntilExpiration).isPositive();
            assertThat(timeUntilExpiration).isLessThanOrEqualTo(3600000L);
        }
    }
}
