package com.ldsilver.chingoohaja.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuthConfigResponse(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("redirect_uri") String redirectUri,
        @JsonProperty("scope") String scope,
        @JsonProperty("state") String state,
        @JsonProperty("code_challenge") String codeChallenge,
        @JsonProperty("code_verifier") String codeVerifier,
        @JsonProperty("code_challenge_method") String codeChallengeMethod,
        @JsonProperty("authorization_url") String authorizationUrl
) {
    private static final String CODE_CHALLENGE_METHOD = "S256";

    public OAuthConfigResponse {
        codeChallengeMethod = CODE_CHALLENGE_METHOD;
    }

    public static OAuthConfigResponse of(
            String clientId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeVerifier,
            String authorizationUrl) {
        return new OAuthConfigResponse(
                clientId,
                redirectUri,
                scope,
                state,
                codeChallenge,
                codeVerifier,
                CODE_CHALLENGE_METHOD,
                authorizationUrl
        );
    }

    /**
     * 프론트엔드용 간소화된 정보 (민감한 정보 제외)
     * @return 공개 가능한 OAuth 설정 정보
     */
    public OAuthConfigResponse forPublic() {
        return new OAuthConfigResponse(
                clientId,
                redirectUri,
                scope,
                state,
                codeChallenge,
                null, // code_verifier는 서버에서만 사용
                codeChallengeMethod,
                authorizationUrl
        );
    }

    public boolean usesPKCE() {
        return codeChallenge != null && !codeChallenge.trim().isEmpty();
    }

}
