package com.ldsilver.chingoohaja.controller;

import com.ldsilver.chingoohaja.dto.auth.request.SocialLoginRequest;
import com.ldsilver.chingoohaja.dto.auth.response.OAuthConfigResponse;
import com.ldsilver.chingoohaja.dto.auth.response.SocialLoginResponse;
import com.ldsilver.chingoohaja.dto.common.ApiResponse;
import com.ldsilver.chingoohaja.service.OAuthConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "소셜 로그인 및 JWT 토큰 관리 API")
public class AuthController {

    private final OAuthConfigService oAuthConfigService;

    @Operation(
            summary = "OAuth 설정 정보 조회",
            description = "프론트엔드에서 소셜 로그인을 위한 OAuth 설정 정보를 제공합니다."
    )
    @GetMapping("/oauth/{provider}/config")
    public ApiResponse<OAuthConfigResponse> getOAuthConfig(
            @Parameter(description = "OAuth 공급자", example = "kakao")
            @PathVariable String provider, Principal principal) {
        log.debug("OAuth 설정 정보 요청 - {}", provider);

        OAuthConfigResponse config = oAuthConfigService.getOAuthConfig(provider);
        return ApiResponse.ok("OAuth 설정 정보 조회 성공", config);
    }

    @Operation(
            summary = "소셜 로그인",
            description = "OAuth 인가 코드를 사용하여 소셜 로그인을 처리하고 JWT 토큰을 발급합니다."
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

        return ApiResponse.ok("로그인 성공");
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
}
