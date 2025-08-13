package com.ldsilver.chingoohaja.dto.oauth.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record TokenResponse (
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("issued_at") LocalDateTime issuedAt
) {
    public TokenResponse {
        tokenType = "Bearer";
        issuedAt = issuedAt != null ? issuedAt : LocalDateTime.now();
    }

    public static TokenResponse of(String accessToken, String refreshToken, Long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, LocalDateTime.now());
    }

    public static TokenResponse accessTokenOnly(String accessToken, Long expiresIn) {
        return new TokenResponse(accessToken, null, "Bearer", expiresIn, LocalDateTime.now());
    }

    public static TokenResponse forRefresh(String newAccessToken, String existingRefreshToken, Long expiresIn) {
        return new TokenResponse(newAccessToken, existingRefreshToken, "Bearer", expiresIn, LocalDateTime.now());
    }

    public String maskedAccessToken() {
        return maskToken(accessToken);
    }

    public String maskedRefreshToken() {
        return maskToken(refreshToken);
    }

    public String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    @Override
    public String toString() {
        return "TokenResponse{" +
                "accessToken='" + maskedAccessToken() + '\'' +
                ", refreshToken='" + maskedRefreshToken() + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", issuedAt=" + issuedAt +
                '}';

    }
}
