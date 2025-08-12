package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.config.OAuthProperties;
import com.ldsilver.chingoohaja.dto.auth.response.OAuthConfigResponse;
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
        PKCEPair pkcePair = generatePKCE();
        String authorizationUrl = kakao.getAuthorizationUrl(state, pkcePair.codeChallenge());

        return OAuthConfigResponse.of(
                kakao.getClientId(),
                kakao.getRedirectUri(),
                kakao.getScope(),
                state,
                pkcePair.codeChallenge(),
                pkcePair.codeVerifier(),
                authorizationUrl
        );
    }

    private OAuthConfigResponse createGoogleConfig() {
        OAuthProperties.GoogleProperties google = oAuthProperties.getGoogle();

        String state = generateState();
        PKCEPair pkcePair = generatePKCE();
        String authorizationUrl = google.getAuthorizationUrl(state, pkcePair.codeChallenge());

        return OAuthConfigResponse.of(
                google.getClientId(),
                google.getRedirectUri(),
                google.getScope(),
                state,
                pkcePair.codeChallenge(),
                pkcePair.codeVerifier(),
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
     * PKCE (Proof Key for Code Exchange) 생성
     * @return PKCEPair (code_verifier와 code_challenge)
     */
    private PKCEPair generatePKCE() {
        // 1. Code Verifier 생성 (43-128자 랜덤 문자열)
        byte[] codeVerifierBytes = new byte[32];
        secureRandom.nextBytes(codeVerifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(codeVerifierBytes);

        // 2. Code Challenge 생성 (SHA256(Code Verifier)의 Base64 인코딩)
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

    /**
     * PKCE Code Verifier와 Code Challenge 쌍
     */
    private record PKCEPair(String codeVerifier, String codeChallenge) {}

}
