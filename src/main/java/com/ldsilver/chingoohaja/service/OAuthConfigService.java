package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.OAuthProperties;
import com.ldsilver.chingoohaja.dto.auth.response.OAuthConfigResponse;
import com.ldsilver.chingoohaja.infrastructure.oauth.OAuthClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthConfigService {

    private final OAuthProperties oAuthProperties;
    private final OAuthClientFactory oAuthClientFactory;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthConfigResponse getOAuthConfig(String provider) {
        if (!oAuthClientFactory.isProviderSupported(provider)) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED, provider);
        }

        String normalizedProvider = provider.toLowerCase().trim();

        return switch (normalizedProvider) {
            case "kakao" -> createKakaoConfig();
            case "google" -> createGoogleConfig();
            default -> throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED, provider);
        };
    }

    private OAuthConfigResponse createKakaoConfig() {
        OAuthProperties.KakaoProperties kakao = oAuthProperties.getKakao();

        String state = generateState();
        String codeChallenge = generateCodeChallenge();
        String authorizationUrl = kakao.getAuthorizationUrl(state, codeChallenge);

        return OAuthConfigResponse.of(
                kakao.getClientId(),
                kakao.getRedirectUri(),
                kakao.getScope(),
                state,
                codeChallenge,
                authorizationUrl
        );
    }

    private OAuthConfigResponse createGoogleConfig() {
        OAuthProperties.GoogleProperties google = oAuthProperties.getGoogle();

        String state = generateState();
        String codeChallenge = generateCodeChallenge();
        String authorizationUrl = google.getAuthorizationUrl(state, codeChallenge);

        return OAuthConfigResponse.of(
                google.getClientId(),
                google.getRedirectUri(),
                google.getScope(),
                state,
                codeChallenge,
                authorizationUrl
        );
    }

    /**
     * 보안을 위한 State 파라미터 생성 (CSRF 방지)
     * @return 32바이트 랜덤 문자열 (Base64 URL-safe 인코딩)
     */
    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * PKCE Code Challenge 생성
     * @return 32바이트 랜덤 문자열 (Base64 URL-safe 인코딩)
     */
    private String generateCodeChallenge() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
