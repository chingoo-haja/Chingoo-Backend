package com.ldsilver.chingoohaja.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuthConfigResponse(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("redirect_uri") String redirectUri,
        @JsonProperty("scope") String scope,
        @JsonProperty("state") String state,
        @JsonProperty("code_challenge") String codeChallenge,
        @JsonProperty("code_challenge_method") String codeChallengeMethod,
        @JsonProperty("authorization_url") String authorizationUrl
) {
    public OAuthConfigResponse {
        codeChallengeMethod = "S256"; // 고정값
    }

    public static OAuthConfigResponse of(
            String clientId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String authorizationUrl) {
        return new OAuthConfigResponse(
                clientId,
                redirectUri,
                scope,
                state,
                codeChallenge,
                "S256",
                authorizationUrl
        );
    }
}
