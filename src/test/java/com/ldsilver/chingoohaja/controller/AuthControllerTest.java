package com.ldsilver.chingoohaja.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.CookieProperties;
import com.ldsilver.chingoohaja.dto.auth.request.LoginRequest;
import com.ldsilver.chingoohaja.dto.auth.request.SignUpRequest;
import com.ldsilver.chingoohaja.dto.auth.response.LoginResponse;
import com.ldsilver.chingoohaja.dto.oauth.request.LogoutRequest;
import com.ldsilver.chingoohaja.dto.oauth.request.NativeGoogleLoginRequest;
import com.ldsilver.chingoohaja.dto.oauth.request.NativeSocialLoginRequest;
import com.ldsilver.chingoohaja.dto.oauth.request.SocialLoginRequest;
import com.ldsilver.chingoohaja.dto.oauth.response.*;
import com.ldsilver.chingoohaja.infrastructure.jwt.JwtAuthenticationFilter;
import com.ldsilver.chingoohaja.service.AuthService;
import com.ldsilver.chingoohaja.service.LocalAuthService;
import com.ldsilver.chingoohaja.service.OAuthConfigService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AuthController 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LocalAuthService localAuthService;

    @MockitoBean
    private OAuthConfigService oAuthConfigService;

    @MockitoBean
    private CookieProperties cookieProperties;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private SocialLoginResponse.UserInfo createUserInfo(boolean isNewUser) {
        return new SocialLoginResponse.UserInfo(
                1L, "test@test.com", "testuser", "테스트유저",
                null, "USER", "local", isNewUser,
                true, "테스트유저", 25, "MALE");
    }

    private LoginResponse createLoginResponse() {
        return LoginResponse.of(
                "access-token-123", "refresh-token-456", 3600L,
                createUserInfo(false));
    }

    @Nested
    @DisplayName("POST /api/v1/auth/signup - 회원가입")
    class SignUp {

        @Test
        @DisplayName("이메일과 비밀번호로 회원가입한다")
        void signUp_thenReturnsLoginResponse() throws Exception {
            // given
            SignUpRequest request = new SignUpRequest("newuser@test.com", "Password1!", "새유저");
            LoginResponse response = createLoginResponse();
            given(localAuthService.signUp(any(SignUpRequest.class))).willReturn(response);
            given(cookieProperties.isSecure()).willReturn(false);
            given(cookieProperties.getSameSite()).willReturn("Lax");
            given(cookieProperties.getMaxAge()).willReturn(2592000);

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.access_token").value("access-token-123"))
                    .andExpect(jsonPath("$.data.refresh_token").doesNotExist())
                    .andExpect(jsonPath("$.message").value("회원가입 성공"))
                    .andExpect(header().exists("Set-Cookie"));
        }

        @Test
        @DisplayName("이메일 형식이 잘못되면 400 에러를 반환한다")
        void signUp_whenInvalidEmail_thenReturnsBadRequest() throws Exception {
            // given
            String invalidRequest = "{\"email\": \"invalid\", \"password\": \"Password1!\", \"real_name\": \"테스트\"}";

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호가 없으면 400 에러를 반환한다")
        void signUp_whenNoPassword_thenReturnsBadRequest() throws Exception {
            // given
            String invalidRequest = "{\"email\": \"test@test.com\", \"real_name\": \"테스트\"}";

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login - 로그인")
    class Login {

        @Test
        @DisplayName("이메일과 비밀번호로 로그인한다")
        void login_thenReturnsLoginResponse() throws Exception {
            // given
            LoginRequest request = new LoginRequest("test@test.com", "Password1!");
            LoginResponse response = createLoginResponse();
            given(localAuthService.login(any(LoginRequest.class))).willReturn(response);
            given(cookieProperties.isSecure()).willReturn(false);
            given(cookieProperties.getSameSite()).willReturn("Lax");
            given(cookieProperties.getMaxAge()).willReturn(2592000);

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.access_token").value("access-token-123"))
                    .andExpect(jsonPath("$.data.refresh_token").doesNotExist())
                    .andExpect(jsonPath("$.message").value("로그인 성공"));
        }

        @Test
        @DisplayName("이메일이 없으면 400 에러를 반환한다")
        void login_whenNoEmail_thenReturnsBadRequest() throws Exception {
            // given
            String invalidRequest = "{\"password\": \"Password1!\"}";

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/oauth/{provider}/config - OAuth 설정 정보 조회")
    class GetOAuthConfig {

        @Test
        @DisplayName("카카오 OAuth 설정 정보를 조회한다")
        void getOAuthConfig_kakao_thenReturnsConfig() throws Exception {
            // given
            OAuthConfigResponse config = new OAuthConfigResponse(
                    "kakao-client-id", "http://localhost:3000/oauth/callback",
                    "profile_nickname profile_image account_email",
                    "random-state-123", "code-challenge", "code-verifier", "S256",
                    "https://kauth.kakao.com/oauth/authorize?...");
            given(oAuthConfigService.getOAuthConfig("kakao", false)).willReturn(config);

            // when & then
            mockMvc.perform(get("/api/v1/auth/oauth/kakao/config")
                            .param("platform", "web"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.client_id").value("kakao-client-id"))
                    .andExpect(jsonPath("$.data.state").value("random-state-123"));
        }

        @Test
        @DisplayName("모바일 플랫폼용 OAuth 설정 정보를 조회한다")
        void getOAuthConfig_mobile_thenReturnsConfig() throws Exception {
            // given
            OAuthConfigResponse config = new OAuthConfigResponse(
                    "kakao-client-id", "com.chingoohaja.app://oauth/callback/kakao",
                    "profile_nickname profile_image account_email",
                    "random-state-456", "code-challenge", "code-verifier", "S256",
                    "https://kauth.kakao.com/oauth/authorize?...");
            given(oAuthConfigService.getOAuthConfig("kakao", true)).willReturn(config);

            // when & then
            mockMvc.perform(get("/api/v1/auth/oauth/kakao/config")
                            .param("platform", "mobile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.redirect_uri").value("com.chingoohaja.app://oauth/callback/kakao"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/oauth/{provider} - 소셜 로그인")
    class SocialLogin {

        @Test
        @DisplayName("카카오 소셜 로그인을 처리한다")
        void socialLogin_kakao_thenReturnsResponse() throws Exception {
            // given
            SocialLoginRequest request = SocialLoginRequest.of(
                    "auth-code-123-abcdefghijklmnopqrstuvwxyz-1234567890",
                    "random-state-abcdefghijklmnopqrstuvwxyz");
            SocialLoginResponse response = SocialLoginResponse.of(
                    "access-token", "refresh-token", 3600L, createUserInfo(false));
            given(authService.socialLogin(eq("kakao"), any(SocialLoginRequest.class)))
                    .willReturn(response);
            given(cookieProperties.isSecure()).willReturn(false);
            given(cookieProperties.getSameSite()).willReturn("Lax");
            given(cookieProperties.getMaxAge()).willReturn(2592000);

            // when & then
            mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.access_token").value("access-token"))
                    .andExpect(jsonPath("$.data.refresh_token").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh - 토큰 갱신")
    class RefreshToken {

        @Test
        @DisplayName("쿠키의 refresh token으로 토큰을 갱신한다")
        void refreshToken_thenReturnsNewTokens() throws Exception {
            // given
            TokenResponse tokenResponse = TokenResponse.of(
                    "new-access-token", "new-refresh-token", 3600L);
            given(authService.refreshToken(any())).willReturn(tokenResponse);
            given(cookieProperties.isSecure()).willReturn(false);
            given(cookieProperties.getSameSite()).willReturn("Lax");
            given(cookieProperties.getMaxAge()).willReturn(2592000);

            // when & then
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.access_token").value("new-access-token"))
                    .andExpect(jsonPath("$.data.refresh_token").doesNotExist());
        }

        @Test
        @DisplayName("쿠키에 refresh token이 없으면 예외를 발생시킨다")
        void refreshToken_whenNoCookie_thenThrowsException() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout - 로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃을 처리한다")
        void logout_thenSuccess() throws Exception {
            // given
            willDoNothing().given(authService).logout(any(), any(), any());

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer access-token-123")
                            .cookie(new Cookie("refreshToken", "refresh-token-456"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"logout_all\": false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("로그아웃 성공"));
        }

        @Test
        @DisplayName("request body 없이 로그아웃을 처리한다")
        void logout_withoutBody_thenSuccess() throws Exception {
            // given
            willDoNothing().given(authService).logout(any(), any(), any());

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer access-token-123")
                            .cookie(new Cookie("refreshToken", "refresh-token-456")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/validate - 토큰 검증")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰을 검증한다")
        void validateToken_whenValid_thenReturnsValidation() throws Exception {
            // given
            TokenValidationResponse response = new TokenValidationResponse(
                    true, 1L, "test@test.com", "USER",
                    LocalDateTime.of(2025, 6, 1, 13, 0));
            given(authService.validateToken("access-token-123")).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/auth/validate")
                            .header("Authorization", "Bearer access-token-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.is_valid").value(true))
                    .andExpect(jsonPath("$.data.user_id").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me - 내 정보 조회")
    class GetMyInfo {

        @Test
        @DisplayName("현재 로그인된 사용자의 정보를 조회한다")
        void getMyInfo_thenReturnsUserInfo() throws Exception {
            // given
            UserMeResponse response = new UserMeResponse(
                    1L, "test@test.com", "testuser", "테스트유저",
                    null, "USER", "local", true,
                    "테스트유저", 25, "010-1234-5678",
                    LocalDate.of(2000, 1, 1), "MALE",
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 6, 1, 0, 0));
            given(authService.getMyInfo("access-token-123")).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer access-token-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.email").value("test@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/oauth/kakao/native - 네이티브 카카오 로그인")
    class NativeKakaoLogin {

        @Test
        @DisplayName("네이티브 카카오 로그인을 처리한다")
        void nativeKakaoLogin_thenReturnsResponse() throws Exception {
            // given
            NativeSocialLoginRequest request = new NativeSocialLoginRequest(
                    "kakao-access-token", null);
            SocialLoginResponse response = SocialLoginResponse.of(
                    "access-token", "refresh-token", 3600L, createUserInfo(true));
            given(authService.nativeKakaoLogin(any(NativeSocialLoginRequest.class)))
                    .willReturn(response);
            given(cookieProperties.isSecure()).willReturn(false);
            given(cookieProperties.getSameSite()).willReturn("Lax");
            given(cookieProperties.getMaxAge()).willReturn(2592000);

            // when & then
            mockMvc.perform(post("/api/v1/auth/oauth/kakao/native")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.access_token").value("access-token"))
                    .andExpect(jsonPath("$.data.user_info.is_new_user").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/oauth/google/native - 네이티브 구글 로그인")
    class NativeGoogleLogin {

        @Test
        @DisplayName("네이티브 구글 로그인을 처리한다")
        void nativeGoogleLogin_thenReturnsResponse() throws Exception {
            // given
            NativeGoogleLoginRequest request = new NativeGoogleLoginRequest(
                    "google-id-token", null);
            SocialLoginResponse response = SocialLoginResponse.of(
                    "access-token", "refresh-token", 3600L, createUserInfo(false));
            given(authService.nativeGoogleLogin(any(NativeGoogleLoginRequest.class)))
                    .willReturn(response);
            given(cookieProperties.isSecure()).willReturn(false);
            given(cookieProperties.getSameSite()).willReturn("Lax");
            given(cookieProperties.getMaxAge()).willReturn(2592000);

            // when & then
            mockMvc.perform(post("/api/v1/auth/oauth/google/native")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.access_token").value("access-token"));
        }
    }
}
