package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.auth.request.LogoutRequest;
import com.ldsilver.chingoohaja.dto.auth.request.RefreshTokenRequest;
import com.ldsilver.chingoohaja.dto.auth.request.SocialLoginRequest;
import com.ldsilver.chingoohaja.dto.auth.response.*;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.AuthService;
import com.ldsilver.chingoohaja.service.OAuthConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "소셜 로그인 및 JWT 토큰 관리 API")
public class AuthController {

    private final AuthService authService;
    private final OAuthConfigService oAuthConfigService;
    private final ReactiveUserDetailsPasswordService reactiveUserDetailsPasswordService;

    @Operation(
            summary = "OAuth 설정 정보 조회",
            description = "프론트엔드에서 소셜 로그인을 위한 OAuth 설정 정보를 제공합니다. " +
                    "State, Code Challenge 등 보안 파라미터가 포함됩니다."
    )
    @GetMapping("/oauth/{provider}/config")
    public ApiResponse<OAuthConfigResponse> getOAuthConfig(
            @Parameter(description = "OAuth 공급자", example = "kakao")
            @PathVariable String provider) {
        log.debug("OAuth 설정 정보 요청 - {}", provider);

        OAuthConfigResponse config = oAuthConfigService.getOAuthConfig(provider);
        return ApiResponse.ok("OAuth 설정 정보 조회 성공", config);
    }


    @Operation(
            summary = "소셜 로그인",
            description = "OAuth 인가 코드를 사용하여 소셜 로그인을 처리하고 JWT 토큰을 발급합니다." +
                    "기존 사용자는 로그인, 신규 사용자는 회원가입과 동시에 로그인됩니다."
    )
    @PostMapping("/oauth/{provider}")
    public ApiResponse<SocialLoginResponse> socialLogin(
            @Parameter(description = "OAuth 공급자", example = "kakao")
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest httpRequest) {

        log.debug("소셜 로그인 요청 - provider: {}, state: {}", provider, request.getState());

        // 클라이언트 IP 설정
        request.setClientIp(getClientIpAddress(httpRequest));

        SocialLoginResponse response = authService.socialLogin(provider, request);

        return ApiResponse.ok("로그인 성공", response);
    }


    @Operation(
            summary = "토큰 갱신",
            description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다."
    )
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.debug("토큰 갱신 요청");

        TokenResponse response = authService.refreshToken(request);
        return ApiResponse.ok("토큰 갱신 성공",response);
    }


    @Operation(
            summary = "로그아웃",
            description = "현재 사용중인 코튼을 무효화힙니다. " +
                    "logout_all이 true인 경우 모든 다이바이스에서 로그아웃됩니다.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest ) {
        log.debug("로그아웃 요청 - logoutAll: {}", request.isLogoutAll());

        String accessToken = extractAccessTokenFromRequest(httpRequest);
        authService.logout(accessToken, request);

        return ApiResponse.ok("로그아웃 성공");
    }


    @Operation(
            summary = "토큰 검증",
            description = "현재 Access Token의 유효성을 검증합니다.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/validate")
    public ApiResponse<TokenValidationResponse> validateToken(
            HttpServletRequest httpServletRequest) {

        String accessToken = extractAccessTokenFromRequest(httpServletRequest);
        // TokenValidationResponse response = authService.validateToken(accessToken);

        return ApiResponse.ok("토큰 검증 완료"
//        , response
        );
    }


    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인된 사용자의 기본 정보를 조회합니다.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMyInfo(
            HttpServletRequest httpServletRequest) {

        String accessToken = extractAccessTokenFromRequest(httpServletRequest);
        // UserMeResponse response = authService.getMyInfo(accessToken);

        return ApiResponse.ok("사용자 정보 조회 성공"
//        , response
        );
    }

    /**
     * 클라이언트 실제 IP 주소 추출
     * 프록시나 로드밸런서를 거치는 경우를 고려
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        if (xForwardedProto != null) {
            // 프록시 환경
            return request.getHeader("X-Forwarded-For") != null ?
                    request.getHeader("X-Forwarded-For") : request.getRemoteAddr();
        }

        return request.getRemoteAddr();
    }

    private String extractAccessTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
