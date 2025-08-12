package com.ldsilver.chingoohaja.infrastructure.oauth;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import com.ldsilver.chingoohaja.common.util.EmailMaskingUtils;
import com.ldsilver.chingoohaja.config.OAuthProperties;
import com.ldsilver.chingoohaja.dto.auth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.auth.response.KakaoApiResponse;
import com.ldsilver.chingoohaja.dto.auth.response.TokenResponse;
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
public class KakaoOAuthClient implements OAuthClient{

    private final WebClient webClient;
    private final OAuthProperties oAuthProperties;

    @Override
    public TokenResponse exchangeCodeForToken(String code, String codeVerifier) {
        log.debug("카카오 토큰 교환 시작 - code:{}", maskCode(code));

        MultiValueMap<String, String> formData = createTokenRequestForm(code, codeVerifier);

        try {
            TokenResponse response = webClient
                    .post()
                    .uri(oAuthProperties.getKakao().getTokenUrl())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            validateTokenResponse(response);

            log.debug("카카오 토큰 교환 성공");
            return response;
        } catch (WebClientResponseException e) {
            log.error("카카오 토큰 교환 실패 - 상태코드: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        } catch (Exception e) {
            log.error("카카오 토큰 교환 중 예외 발생", e);
            throw new CustomException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        log.debug("카카오 사용자 정보 조회 시작");

        try {
            KakaoApiResponse response = webClient
                    .get()
                    .uri(oAuthProperties.getKakao().getUserInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .retrieve()
                    .bodyToMono(KakaoApiResponse.class)
                    .block();

            if (response == null) {
                throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
            }

            OAuthUserInfo userInfo = OAuthUserInfo.fromKakao(response);
            validateUserInfo(userInfo);

            log.debug("카카오 사용자 정보 조회 성공 - email: {}", EmailMaskingUtils.maskEmailForLog(userInfo.email()));
            return userInfo;
        } catch (WebClientResponseException e) {
            log.error("카카오 사용자 정보 조회 실패 - 상태코드: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("카카오 사용자 정보 조회 중 예외 발생", e);
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED);
        }
    }

    @Override
    public String getProviderName() {
        return "kakao";
    }


    private MultiValueMap<String, String> createTokenRequestForm(String code, String codeVerifier) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", oAuthProperties.getKakao().getClientId());
        formData.add("client_secret", oAuthProperties.getKakao().getClientSecret());
        formData.add("redirect_uri", oAuthProperties.getKakao().getRedirectUri());
        formData.add("code", code);

        if (codeVerifier != null && !codeVerifier.trim().isEmpty()) {
            formData.add("code_verifier", codeVerifier);
        }

        return formData;
    }

    private void validateTokenResponse(TokenResponse response) {
        if (response == null || response.accessToken() == null) {
            throw new CustomException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED, "카카오 토큰 교환 응답이 비어있습니다.");
        }
    }

    private void validateUserInfo(OAuthUserInfo userInfo) {
        if (userInfo.providerId() == null || userInfo.providerId().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED, "카카오 사용자 ID가 없습니다.");
        }

        if (userInfo.email() == null || userInfo.email().trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED, "카카오 이메일 정보가 없습니다. 이메일 제공 동의가 필요합니다.");
        }
    }

    private String maskCode(String code) {
        if (code == null || code.length() < 8) return "***";
        return code.substring(0, 4) + "***" + code.substring(code.length() - 4);
    }

}
