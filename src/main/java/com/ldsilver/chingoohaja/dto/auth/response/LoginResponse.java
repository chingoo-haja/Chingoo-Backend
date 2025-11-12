package com.ldsilver.chingoohaja.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ldsilver.chingoohaja.dto.oauth.response.SocialLoginResponse;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("issued_at") LocalDateTime issuedAt,
        @JsonProperty("user_info") SocialLoginResponse.UserInfo userInfo
) {
    public LoginResponse {
        tokenType = "Bearer";
        issuedAt = issuedAt != null ? issuedAt : LocalDateTime.now();
    }

    public static LoginResponse of(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            SocialLoginResponse.UserInfo userInfo) {
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                LocalDateTime.now(),
                userInfo
        );
    }

    public LoginResponse withoutRefreshToken() {
        return new LoginResponse(
                this.accessToken,
                null,
                this.tokenType,
                this.expiresIn,
                this.issuedAt,
                this.userInfo
        );
    }
}
