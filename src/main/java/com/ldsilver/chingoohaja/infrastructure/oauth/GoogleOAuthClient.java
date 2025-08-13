package com.ldsilver.chingoohaja.infrastructure.oauth;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.common.util.EmailMaskingUtils;
import com.ldsilver.chingoohaja.config.OAuthProperties;
import com.ldsilver.chingoohaja.dto.oauth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.oauth.response.GoogleApiResponse;
import com.ldsilver.chingoohaja.dto.oauth.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient{

    private final WebClient webClient;
    private final OAuthProperties oAuthProperties;

    @Override
    public TokenResponse exchangeCodeForToken(String code, String codeVerifier) {
        log.debug("구글 토큰 교환 시작 - code: {}", maskCode(code));

        MultiValueMap<String, String> formData = createTokenRequestForm(code, codeVerifier);

        try {
            TokenResponse response = webClient
                    .post()
                    .uri(oAuthProperties.getGoogle().getTokenUrl())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            validateTokenResponse(response);

            log.debug("구글 토큰 교환 성공");
            return response; // 바로 반환

        } catch (WebClientResponseException e) {
            log.error("구글 토큰 교환 실패 - 상태코드: {}, 응답: {}", e.getStatusCode(), safeBody(e.getResponseBodyAsString()));
            throw new CustomException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        } catch (Exception e) {
            log.error("구글 토큰 교환 중 예외 발생", e);
            throw new CustomException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        log.debug("구글 사용자 정보 조회 시작");

        try {
            GoogleApiResponse response = webClient
                    .get()
                    .uri(oAuthProperties.getGoogle().getUserInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(GoogleApiResponse.class)
                    .block();

            if (response == null) {
                throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
            }

            OAuthUserInfo userInfo = OAuthUserInfo.fromGoogle(response);
            validateUserInfo(userInfo);

            log.debug("구글 사용자 정보 조회 성공 - email: {}", EmailMaskingUtils.maskEmailForLog(userInfo.email()));
            return userInfo;

        } catch (WebClientResponseException e) {
            log.error("구글 사용자 정보 조회 실패 - 상태코드: {}, 응답: {}", e.getStatusCode(), safeBody(e.getResponseBodyAsString()));
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("구글 사용자 정보 조회 중 예외 발생", e);
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
        }
    }

    @Override
    public String getProviderName() {
        return "google";
    }


    private MultiValueMap<String, String> createTokenRequestForm(String code, String codeVerifier) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", oAuthProperties.getGoogle().getClientId());
        formData.add("client_secret", oAuthProperties.getGoogle().getClientSecret());
        formData.add("redirect_uri", oAuthProperties.getGoogle().getRedirectUri());
        formData.add("code", code);

        if (codeVerifier != null && !codeVerifier.trim().isEmpty()) {
            formData.add("code_verifier", codeVerifier);
        }

        return formData;
    }

    private void validateTokenResponse(TokenResponse response) {
        if (response == null || response.accessToken() == null) {
            throw new CustomException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    private void validateUserInfo(OAuthUserInfo userInfo) {
        if (userInfo.providerId() == null || userInfo.providerId().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
        }

        if (userInfo.email() == null || userInfo.email().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
        }
    }

    private String maskCode(String code) {
        if (code == null || code.length() < 8) return "***";
        return code.substring(0, 4) + "***" + code.substring(code.length() - 4);
    }

    private String safeBody(String body) {
        if (body == null) return null;
        String sanitized = body
                .replaceAll("(?i)(\"access_token\"\\s*:\\s*\").*?(\")", "$1***$2")
                .replaceAll("(?i)(\"refresh_token\"\\s*:\\s*\").*?(\")", "$1***$2")
                .replaceAll("(?i)(\"email\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        return sanitized.length() > 512 ? sanitized.substring(0, 512) + "...(truncated)" : sanitized;
    }

}
