package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.OAuthProperties;
import com.ldsilver.chingoohaja.dto.oauth.response.OAuthConfigResponse;
import com.ldsilver.chingoohaja.infrastructure.oauth.OAuthClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthConfigService {

    private final OAuthProperties oAuthProperties;
    private final OAuthClientFactory oAuthClientFactory;
    private final SecureRandom secureRandom = new SecureRandom();

    // ✅ 플랫폼 파라미터 추가
    public OAuthConfigResponse getOAuthConfig(String provider, boolean isMobile) {
        if (!oAuthClientFactory.isProviderSupported(provider)) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED, provider);
        }

        String normalizedProvider = provider.toLowerCase().trim();

        return switch (normalizedProvider) {
            case "kakao" -> createKakaoConfig(isMobile);
            case "google" -> createGoogleConfig(isMobile);
            default -> throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED, provider);
        };
    }

    // 기존 메서드 호환성 유지 (웹 기본)
    public OAuthConfigResponse getOAuthConfig(String provider) {
        return getOAuthConfig(provider, false);
    }

    private OAuthConfigResponse createKakaoConfig(boolean isMobile) {
        OAuthProperties.KakaoProperties kakao = oAuthProperties.getKakao();

        String state = generateState();
        PKCEPair pkcePair = generatePKCE();
        String redirectUri = kakao.getRedirectUri(isMobile);
        String authorizationUrl = kakao.getAuthorizationUrl(state, pkcePair.codeChallenge(), isMobile);

        log.debug("카카오 OAuth 설정 생성 - isMobile: {}, redirectUri: {}", isMobile, redirectUri);

        return OAuthConfigResponse.of(
                kakao.getClientId(),
                redirectUri,
                kakao.getScope(),
                state,
                pkcePair.codeChallenge(),
                pkcePair.codeVerifier(),
                authorizationUrl
        );
    }

    private OAuthConfigResponse createGoogleConfig(boolean isMobile) {
        OAuthProperties.GoogleProperties google = oAuthProperties.getGoogle();

        String state = generateState();
        PKCEPair pkcePair = generatePKCE();
        String redirectUri = google.getRedirectUri(isMobile);
        String authorizationUrl = google.getAuthorizationUrl(state, pkcePair.codeChallenge(), isMobile);

        log.debug("구글 OAuth 설정 생성 - isMobile: {}, redirectUri: {}", isMobile, redirectUri);

        return OAuthConfigResponse.of(
                google.getClientId(),
                redirectUri,
                google.getScope(),
                state,
                pkcePair.codeChallenge(),
                pkcePair.codeVerifier(),
                authorizationUrl
        );
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PKCEPair generatePKCE() {
        byte[] codeVerifierBytes = new byte[32];
        secureRandom.nextBytes(codeVerifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(codeVerifierBytes);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] challengeBytes = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            String codeChallenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(challengeBytes);

            log.debug("PKCE 생성 - verifier 길이: {}, challenge 길이: {}",
                    codeVerifier.length(), codeChallenge.length());

            return new PKCEPair(codeVerifier, codeChallenge);

        } catch (Exception e) {
            log.error("PKCE 생성 실패", e);
            throw new CustomException(ErrorCode.OAUTH_PKCE_GENERATION_FAILED);
        }
    }

    private record PKCEPair(String codeVerifier, String codeChallenge) {}
}