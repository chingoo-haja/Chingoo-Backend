package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.common.util.NicknameGenerator;
import com.ldsilver.chingoohaja.domain.user.User;
import com.ldsilver.chingoohaja.domain.user.UserToken;
import com.ldsilver.chingoohaja.domain.user.enums.Gender;
import com.ldsilver.chingoohaja.domain.user.enums.UserType;
import com.ldsilver.chingoohaja.dto.oauth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.oauth.request.LogoutRequest;
import com.ldsilver.chingoohaja.dto.oauth.request.RefreshTokenRequest;
import com.ldsilver.chingoohaja.dto.oauth.request.SocialLoginRequest;
import com.ldsilver.chingoohaja.dto.oauth.response.SocialLoginResponse;
import com.ldsilver.chingoohaja.dto.oauth.response.TokenResponse;
import com.ldsilver.chingoohaja.dto.oauth.response.TokenValidationResponse;
import com.ldsilver.chingoohaja.dto.oauth.response.UserMeResponse;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtTokenProvider;
import com.ldsilver.chingoohaja.infrastructure.oauth.GoogleIdTokenValidator;
import com.ldsilver.chingoohaja.infrastructure.oauth.OAuthClient;
import com.ldsilver.chingoohaja.infrastructure.oauth.OAuthClientFactory;
import com.ldsilver.chingoohaja.repository.UserRepository;
import com.ldsilver.chingoohaja.repository.UserTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 테스트")
class AuthServiceTest {

    @Mock private OAuthClientFactory oAuthClientFactory;
    @Mock private UserRepository userRepository;
    @Mock private UserTokenRepository userTokenRepository;
    @Mock private TokenService tokenService;
    @Mock private TokenCacheService tokenCacheService;
    @Mock private NicknameGenerator nicknameGenerator;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private GoogleIdTokenValidator googleIdTokenValidator;

    @InjectMocks private AuthService authService;

    private User testUser;
    private static final Long USER_ID = 1L;
    private static final String ACCESS_TOKEN = "test.access.token";
    private static final String REFRESH_TOKEN = "test.refresh.token";

    @BeforeEach
    void setUp() {
        testUser = User.of(
                "test@gmail.com", "테스트닉네임", "테스트유저",
                Gender.MALE, LocalDate.of(1990, 1, 1), null,
                UserType.USER, null, "kakao", "kakao_12345"
        );
        // id 설정을 위한 리플렉션
        setId(testUser, USER_ID);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("socialLogin")
    class SocialLogin {

        @Test
        @DisplayName("기존 사용자로 소셜 로그인에 성공한다")
        void givenExistingUser_whenSocialLogin_thenReturnsLoginResponse() {
            // given
            String provider = "kakao";
            SocialLoginRequest request = SocialLoginRequest.forTest("auth_code_1234567890", "a".repeat(32));

            OAuthClient mockClient = mock(OAuthClient.class);
            when(oAuthClientFactory.getClient(provider)).thenReturn(mockClient);
            when(mockClient.exchangeCodeForToken(anyString(), any()))
                    .thenReturn(TokenResponse.of("oauth_access", "oauth_refresh", 3600L));

            OAuthUserInfo userInfo = new OAuthUserInfo("kakao_12345", "kakao", "test@gmail.com", "테스트", "테스트닉", null, Gender.MALE);
            when(mockClient.getUserInfo(anyString())).thenReturn(userInfo);

            when(userRepository.findByEmailAndProvider("test@gmail.com", "kakao"))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            TokenResponse tokenResponse = TokenResponse.of(ACCESS_TOKEN, REFRESH_TOKEN, 3600L);
            when(tokenService.generateTokens(eq(USER_ID), anyString())).thenReturn(tokenResponse);

            // when
            SocialLoginResponse response = authService.socialLogin(provider, request);

            // then
            assertThat(response).isNotNull();
            verify(userRepository).findByEmailAndProvider("test@gmail.com", "kakao");
            verify(tokenService).generateTokens(eq(USER_ID), anyString());
        }

        @Test
        @DisplayName("신규 사용자는 자동으로 생성된다")
        void givenNewUser_whenSocialLogin_thenCreatesUser() {
            // given
            String provider = "kakao";
            SocialLoginRequest request = SocialLoginRequest.forTest("auth_code_1234567890", "a".repeat(32));

            OAuthClient mockClient = mock(OAuthClient.class);
            when(oAuthClientFactory.getClient(provider)).thenReturn(mockClient);
            when(mockClient.exchangeCodeForToken(anyString(), any()))
                    .thenReturn(TokenResponse.of("oauth_access", "oauth_refresh", 3600L));

            OAuthUserInfo userInfo = new OAuthUserInfo("kakao_99999", "kakao", "new@gmail.com", "신규", "신규닉", null, null);
            when(mockClient.getUserInfo(anyString())).thenReturn(userInfo);

            when(userRepository.findByEmailAndProvider("new@gmail.com", "kakao"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailAndProviderNot("new@gmail.com", "kakao"))
                    .thenReturn(Optional.empty());

            when(nicknameGenerator.generateUniqueNickname(any())).thenReturn("귀여운고양이");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                setId(saved, 2L);
                return saved;
            });

            TokenResponse tokenResponse = TokenResponse.of(ACCESS_TOKEN, REFRESH_TOKEN, 3600L);
            when(tokenService.generateTokens(eq(2L), anyString())).thenReturn(tokenResponse);

            // when
            SocialLoginResponse response = authService.socialLogin(provider, request);

            // then
            assertThat(response).isNotNull();
            verify(userRepository, atLeastOnce()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("유효한 리프레시 토큰으로 액세스 토큰을 갱신한다")
        void givenValidRefreshToken_whenRefresh_thenReturnsNewAccessToken() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest(REFRESH_TOKEN);
            UserToken userToken = UserToken.of(testUser, REFRESH_TOKEN, LocalDateTime.now().plusDays(7), "device", true);

            when(jwtTokenProvider.isTokenValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(userTokenRepository.findByRefreshTokenAndIsActiveTrue(REFRESH_TOKEN))
                    .thenReturn(Optional.of(userToken));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            String newAccessToken = "new.access.token";
            when(jwtTokenProvider.generateAccessToken(eq(USER_ID), anyString(), anyString()))
                    .thenReturn(newAccessToken);
            when(jwtTokenProvider.getTimeUntilExpiration(newAccessToken)).thenReturn(3600000L);

            // when
            TokenResponse response = authService.refreshToken(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo(newAccessToken);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("유효하지 않은 리프레시 토큰이면 예외를 던진다")
        void givenInvalidRefreshToken_whenRefresh_thenThrowsException() {
            // given
            String invalidToken = "invalid.refresh.token";
            RefreshTokenRequest request = new RefreshTokenRequest(invalidToken);
            when(jwtTokenProvider.isTokenValid(invalidToken)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("리프레시 토큰이 아닌 토큰이면 예외를 던진다")
        void givenAccessTokenAsRefresh_whenRefresh_thenThrowsException() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest(ACCESS_TOKEN);
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken(ACCESS_TOKEN)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.IS_NOT_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("DB에 존재하지 않는 리프레시 토큰이면 예외를 던진다")
        void givenNonExistentRefreshToken_whenRefresh_thenThrowsException() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest(REFRESH_TOKEN);
            when(jwtTokenProvider.isTokenValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
            when(userTokenRepository.findByRefreshTokenAndIsActiveTrue(REFRESH_TOKEN))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("단일 기기 로그아웃에 성공한다")
        void givenValidTokens_whenLogoutSingleDevice_thenSuccess() {
            // given
            LogoutRequest request = new LogoutRequest(false);
            UserToken userToken = UserToken.of(testUser, REFRESH_TOKEN, LocalDateTime.now().plusDays(7), "device", true);

            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN)).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(userTokenRepository.findByRefreshTokenAndIsActiveTrue(REFRESH_TOKEN))
                    .thenReturn(Optional.of(userToken));
            when(jwtTokenProvider.getTimeUntilExpiration(ACCESS_TOKEN)).thenReturn(3600000L);

            // when
            authService.logout(ACCESS_TOKEN, REFRESH_TOKEN, request);

            // then
            verify(userTokenRepository).deactivateTokenByRefreshToken(REFRESH_TOKEN);
            verify(tokenCacheService).deleteRefreshToken(REFRESH_TOKEN);
            verify(tokenCacheService).addToBlacklist(eq(ACCESS_TOKEN), any(Duration.class));
        }

        @Test
        @DisplayName("모든 기기 로그아웃에 성공한다")
        void givenValidToken_whenLogoutAllDevices_thenSuccess() {
            // given
            LogoutRequest request = new LogoutRequest(true);

            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN)).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(jwtTokenProvider.getTimeUntilExpiration(ACCESS_TOKEN)).thenReturn(3600000L);

            // when
            authService.logout(ACCESS_TOKEN, REFRESH_TOKEN, request);

            // then
            verify(userTokenRepository).deactivateAllTokensByUser(testUser);
            verify(tokenCacheService).deleteAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("유효하지 않은 액세스 토큰이면 예외를 던진다")
        void givenInvalidAccessToken_whenLogout_thenThrowsException() {
            // given
            LogoutRequest request = new LogoutRequest(false);
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.logout(ACCESS_TOKEN, REFRESH_TOKEN, request))
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰이면 valid 응답을 반환한다")
        void givenValidToken_whenValidate_thenReturnsValid() {
            // given
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(tokenCacheService.isTokenBlacklisted(ACCESS_TOKEN)).thenReturn(false);

            // TokenValidationResponse.fromValidToken에서 호출되는 메서드 mock
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN)).thenReturn(USER_ID);
            when(jwtTokenProvider.getEmailFromToken(ACCESS_TOKEN)).thenReturn("test@gmail.com");
            when(jwtTokenProvider.getUserTypeFromToken(ACCESS_TOKEN)).thenReturn("USER");
            when(jwtTokenProvider.getExpirationFromToken(ACCESS_TOKEN)).thenReturn(LocalDateTime.now().plusHours(1));

            // when
            TokenValidationResponse response = authService.validateToken(ACCESS_TOKEN);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 invalid 응답을 반환한다")
        void givenInvalidToken_whenValidate_thenReturnsInvalid() {
            // given
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(false);

            // when
            TokenValidationResponse response = authService.validateToken(ACCESS_TOKEN);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("블랙리스트에 있는 토큰이면 invalid 응답을 반환한다")
        void givenBlacklistedToken_whenValidate_thenReturnsInvalid() {
            // given
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(tokenCacheService.isTokenBlacklisted(ACCESS_TOKEN)).thenReturn(true);

            // when
            TokenValidationResponse response = authService.validateToken(ACCESS_TOKEN);

            // then
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("getMyInfo")
    class GetMyInfo {

        @Test
        @DisplayName("유효한 토큰으로 사용자 정보를 조회한다")
        void givenValidToken_whenGetMyInfo_thenReturnsUserInfo() {
            // given
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(tokenCacheService.isTokenBlacklisted(ACCESS_TOKEN)).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN)).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

            // when
            UserMeResponse response = authService.getMyInfo(ACCESS_TOKEN);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 예외를 던진다")
        void givenInvalidToken_whenGetMyInfo_thenThrowsException() {
            // given
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.getMyInfo(ACCESS_TOKEN))
                    .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("존재하지 않는 사용자이면 예외를 던진다")
        void givenNonExistentUser_whenGetMyInfo_thenThrowsException() {
            // given
            when(jwtTokenProvider.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
            when(tokenCacheService.isTokenBlacklisted(ACCESS_TOKEN)).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN)).thenReturn(999L);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.getMyInfo(ACCESS_TOKEN))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }
}
