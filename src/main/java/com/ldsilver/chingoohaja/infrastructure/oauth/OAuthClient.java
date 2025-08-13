package com.ldsilver.chingoohaja.infrastructure.oauth;

import com.ldsilver.chingoohaja.dto.oauth.OAuthUserInfo;
import com.ldsilver.chingoohaja.dto.oauth.response.TokenResponse;

public interface OAuthClient {
    /**
     * 인가 코드를 액세스 토큰으로 교환
     * @param code 인가 코드
     * @param codeVerifier PKCE code verifier (선택)
     * @return OAuth 토큰 정보
     */
    TokenResponse exchangeCodeForToken(String code, String codeVerifier);

    /**
     * 액세스 토큰으로 사용자 정보 조회
     * @param accessToken OAuth 액세스 토큰
     * @return 사용자 정보
     */
    OAuthUserInfo getUserInfo(String accessToken);

    /**
     * 지원하는 OAuth 공급자명 반환
     * @return 공급자명 (kakao, google 등)
     */
    String getProviderName();
}
